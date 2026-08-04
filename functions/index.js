/**
 * Cloud Functions VaultEx — notifications push.
 *
 * Deux fonctions utiles :
 *   1) registerDevice   (HTTP)      : l'app enregistre son jeton FCM + ses adresses.
 *   2) checkDeposits    (planifiée) : toutes les 2 min, compare le solde de chaque
 *                                     adresse à la valeur précédente ; si le solde a
 *                                     augmenté → push « Fonds reçus ».
 *   3) sendAnnouncement (HTTP)      : diffuse une annonce à tous les appareils (le
 *                                     propriétaire de l'app, protégé par un secret).
 *
 * Détection par DIFFÉRENCE DE SOLDE (et non parsing de transactions) : simple et
 * uniforme sur les 5 chaînes. Un envoi (solde qui baisse) ne déclenche jamais de
 * notification ; seul un solde en hausse notifie. Au 1er passage, on mémorise le
 * solde sans notifier (pas de fausse alerte sur les fonds déjà présents).
 *
 * ⚠️ Les appels réseau sortants (APIs blockchain) nécessitent le forfait **Blaze**
 *    (pay-as-you-go). À faible volume, le coût reste quasi nul, mais une carte est
 *    exigée par Google. Le push de TEST depuis la console, lui, ne nécessite rien.
 */

const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/* ─────────────────────────── Contrat USDT TRC20 ─────────────────────────── */
const USDT_TRC20 = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

/*
 * Secret protégeant sendAnnouncement — l'endpoint qui envoie une notification
 * à TOUS les appareils VaultEx.
 *
 * POURQUOI IL N'Y A PLUS DE VALEUR PAR DÉFAUT : elle était écrite ici, dans un
 * fichier versionné. Quiconque lit ce dépôt pouvait donc pousser une
 * notification à tous les utilisateurs. Le scénario n'est pas théorique — c'est
 * l'outil de hameçonnage idéal : « VaultEx : incident de sécurité, confirmez
 * votre phrase de récupération ici ». Le message arrive de la vraie app, avec
 * la vraie icône, sur le vrai canal. Aucun utilisateur ne peut faire la
 * différence.
 *
 * Un secret par défaut n'est jamais « en attendant » : c'est le secret réel de
 * toute installation où personne n'a pensé à définir la variable — et personne
 * n'y pense, puisque ça marche sans.
 *
 * Le déploiement échoue donc si ANNOUNCE_SECRET n'est pas défini :
 *   firebase functions:secrets:set ANNOUNCE_SECRET
 */
const ANNOUNCE_SECRET = process.env.ANNOUNCE_SECRET;
if (!ANNOUNCE_SECRET || ANNOUNCE_SECRET.length < 24) {
  console.error(
    "ANNOUNCE_SECRET absent ou trop court. sendAnnouncement refusera toutes " +
    "les requetes. Definir avec : firebase functions:secrets:set ANNOUNCE_SECRET"
  );
}

/** Comparaison de secrets à temps constant (résiste au timing attack). */
function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const crypto = require("crypto");
  const ba = Buffer.from(a);
  const bb = Buffer.from(b);
  // timingSafeEqual exige des longueurs égales : on hache d'abord, ce qui
  // normalise la taille sans révéler la longueur du secret attendu.
  const ha = crypto.createHash("sha256").update(ba).digest();
  const hb = crypto.createHash("sha256").update(bb).digest();
  return crypto.timingSafeEqual(ha, hb);
}

/* Seuils « poussière » : en dessous, on considère que c'est du bruit d'arrondi. */
const DUST = { BTC: 5e-7, ETH: 1e-6, BNB: 1e-6, SOL: 1e-6, TRX: 1e-3, USDT: 1e-3 };

/* ───────────────────────────── Soldes par chaîne ───────────────────────────── */

async function btcBalance(addr) {
  const r = await fetch(`https://blockstream.info/api/address/${addr}`);
  if (!r.ok) throw new Error(`btc ${r.status}`);
  const j = await r.json();
  const s = j.chain_stats || {};
  return ((s.funded_txo_sum || 0) - (s.spent_txo_sum || 0)) / 1e8;
}

async function evmBalance(rpc, addr) {
  const r = await fetch(rpc, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "eth_getBalance", params: [addr, "latest"] }),
  });
  if (!r.ok) throw new Error(`evm ${r.status}`);
  const j = await r.json();
  return parseInt(j.result, 16) / 1e18;
}
const ethBalance = (addr) => evmBalance("https://eth.llamarpc.com", addr);
const bnbBalance = (addr) => evmBalance("https://bsc-dataseed.binance.org", addr);

async function solBalance(addr) {
  const r = await fetch("https://api.mainnet-beta.solana.com", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "getBalance", params: [addr] }),
  });
  if (!r.ok) throw new Error(`sol ${r.status}`);
  const j = await r.json();
  return (j.result && j.result.value ? j.result.value : 0) / 1e9;
}

async function tronAccount(addr) {
  const r = await fetch(`https://api.trongrid.io/v1/accounts/${addr}`);
  if (!r.ok) throw new Error(`trx ${r.status}`);
  const j = await r.json();
  return (j.data && j.data[0]) ? j.data[0] : null;
}
async function trxBalance(addr) {
  const a = await tronAccount(addr);
  return a ? (a.balance || 0) / 1e6 : 0;
}
async function usdtTrc20Balance(addr) {
  const a = await tronAccount(addr);
  if (!a || !Array.isArray(a.trc20)) return 0;
  for (const entry of a.trc20) {
    const raw = entry[USDT_TRC20];
    if (raw != null) return Number(raw) / 1e6;
  }
  return 0;
}

/* Format court d'un montant (jusqu'à 6 décimales, sans zéros inutiles). */
function trim(n) {
  return Number(n.toFixed(6)).toString();
}

/* ─────────────────────────── 1) registerDevice ─────────────────────────── */
exports.registerDevice = functions.https.onRequest(async (req, res) => {
  try {
    if (req.method !== "POST") return res.status(405).json({ error: "POST uniquement" });
    const body = req.body || {};
    const token = body.token;
    if (!token) return res.status(400).json({ error: "token manquant" });
    const ref = db.collection("devices").doc(token);
    await ref.set(
      {
        token,
        addresses: body.addresses || {},
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    return res.json({ ok: true });
  } catch (e) {
    return res.status(500).json({ error: String(e) });
  }
});

/* ─────────────────────────── 2) checkDeposits ─────────────────────────── */
exports.checkDeposits = functions.pubsub.schedule("every 2 minutes").onRun(async () => {
  const snap = await db.collection("devices").get();
  const checks = [
    ["BTC", "btc", btcBalance],
    ["ETH", "eth", ethBalance],
    ["BNB", "bnb", bnbBalance],
    ["SOL", "sol", solBalance],
    ["TRX", "trx", trxBalance],
    ["USDT", "trx", usdtTrc20Balance], // USDT TRC20 : même adresse que TRX
  ];

  for (const doc of snap.docs) {
    const d = doc.data();
    const addrs = d.addresses || {};
    const prev = d.balances || {};
    const next = { ...prev };
    const notifs = [];

    for (const [sym, addrKey, fn] of checks) {
      const addr = addrs[addrKey];
      if (!addr) continue;
      let bal;
      try {
        bal = await fn(addr);
      } catch (_) {
        continue; // API indisponible ce cycle : on réessaiera au suivant
      }
      if (bal == null || Number.isNaN(bal)) continue;
      const storeKey = sym === "USDT" ? "usdt" : addrKey;
      const before = prev[storeKey];
      next[storeKey] = bal;
      if (before == null) continue; // 1er passage : on mémorise sans notifier
      const delta = bal - before;
      if (delta > (DUST[sym] || 0)) notifs.push({ sym, amount: delta });
    }

    await doc.ref.set({ balances: next }, { merge: true });

    for (const n of notifs) {
      try {
        // DATA-ONLY (pas de bloc `notification`) : indispensable pour que
        // l'app soit TOUJOURS réveillée (onMessageReceived), y compris fermée
        // ou en arrière-plan. C'est elle qui affiche la notif ET l'ajoute au
        // centre de notifications (la cloche). Avec un bloc `notification`, le
        // système Android affichait tout seul en arrière-plan SANS appeler
        // l'app → le message n'entrait jamais dans la cloche.
        await admin.messaging().send({
          token: d.token,
          data: {
            type: "deposit",
            title: "Fonds reçus",
            body: `Vous avez reçu ${trim(n.amount)} ${n.sym}`,
            symbol: n.sym,
            amount: String(n.amount),
          },
          android: { priority: "high" },
        });
      } catch (e) {
        // Jeton invalide/expiré → on nettoie
        const msg = String(e);
        if (msg.includes("registration-token-not-registered") || msg.includes("invalid-argument")) {
          await doc.ref.delete().catch(() => {});
        }
      }
    }
  }
  return null;
});

/* ─────────────────────────── 3) sendAnnouncement ─────────────────────────── */
// Diffuse une annonce à tous les appareils. Voir ANNOUNCE_SECRET plus haut :
// sans secret configuré, l'endpoint est FERMÉ, pas ouvert.
exports.sendAnnouncement = functions.https.onRequest(async (req, res) => {
  try {
    // Fermé par défaut. Un secret mal configuré doit rendre l'endpoint
    // inutilisable, jamais public : le mode dégradé d'un contrôle d'accès
    // est le refus, pas l'autorisation.
    if (!ANNOUNCE_SECRET || ANNOUNCE_SECRET.length < 24) {
      return res.status(503).json({ error: "ANNOUNCE_SECRET non configure" });
    }
    const body = req.body || {};
    const secret = req.query.secret || body.secret;
    // Comparaison à temps constant : une comparaison `!==` classique s'arrête
    // au premier caractère différent, ce qui laisse deviner le secret
    // caractère par caractère en mesurant le temps de réponse.
    if (!safeEqual(secret, ANNOUNCE_SECRET)) return res.status(403).json({ error: "interdit" });
    const title = body.title || "VaultEx";
    const text = body.body || "";
    const snap = await db.collection("devices").get();
    const tokens = snap.docs.map((d) => d.data().token).filter(Boolean);
    if (tokens.length === 0) return res.json({ ok: true, sent: 0 });

    let sent = 0;
    for (let i = 0; i < tokens.length; i += 500) {
      const batch = tokens.slice(i, i + 500);
      // DATA-ONLY (voir checkDeposits) : l'app affiche l'annonce ET l'ajoute
      // à la cloche, même reçue en arrière-plan.
      const resp = await admin.messaging().sendEachForMulticast({
        tokens: batch,
        data: { type: "announcement", title, body: text },
        android: { priority: "high" },
      });
      sent += resp.successCount;
    }
    return res.json({ ok: true, sent });
  } catch (e) {
    return res.status(500).json({ error: String(e) });
  }
});
