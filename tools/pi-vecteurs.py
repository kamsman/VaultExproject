#!/usr/bin/env python3
"""
═══════════════════════════════════════════════════════════════════════════
RECALCULER LES VECTEURS DE TEST DU PI, SANS RIEN CROIRE
═══════════════════════════════════════════════════════════════════════════

    python3 tools/pi-vecteurs.py

Ce script est une SECONDE implémentation, indépendante de celle de
l'application, de tout ce qui mène d'une phrase secrète à une transaction
Pi signée : BIP-39, SLIP-0010 sur Ed25519, StrKey, l'encodage XDR, et la
signature elle-même. Ed25519 y est écrit à la main (RFC 8032) pour ne
dépendre d'aucune bibliothèque.

À QUOI ÇA SERT. Les tests Kotlin PiWalletSep5Test et PiXdrConformiteTest
contiennent des valeurs attendues. Deux d'entre elles viennent du SDK
Stellar officiel ; les autres ont été produites ICI. Sans ce script, ces
dernières seraient des chiffres qu'il faut croire. Avec lui, elles se
recalculent en une seconde, sur n'importe quelle machine, hors ligne.

CE QU'IL VÉRIFIE TOUT SEUL. Lancé sans argument, il rejoue les vecteurs
officiels — dix adresses du SEP-0005, deux enveloppes du SDK Go — et dit
si son propre calcul tombe juste. S'il échoue, ce ne sont pas les tests
Kotlin qu'il faut regarder : c'est que l'une des deux implémentations a
dérivé, et il faut savoir laquelle avant de toucher au reste.

IL NE SERT JAMAIS EN PRODUCTION. Aucune clé réelle ne doit passer ici.
La seule clé privée qu'il manipule est celle, publique, que Stellar
publie dans son dépôt de tests.
═══════════════════════════════════════════════════════════════════════════
"""
import base64, hashlib, hmac, struct, sys, unicodedata


p = 2**255 - 19

def H(m): return hashlib.sha512(m).digest()
def inv(x): return pow(x, p - 2, p)

d = -121665 * inv(121666) % p
I = pow(2, (p - 1) // 4, p)

def xrecover(y):
    xx = (y * y - 1) * inv(d * y * y + 1)
    x = pow(xx, (p + 3) // 8, p)
    if (x * x - xx) % p != 0:
        x = (x * I) % p
    if x % 2 != 0:
        x = p - x
    return x

By = 4 * inv(5) % p
Bx = xrecover(By)
B = (Bx % p, By % p, 1, (Bx * By) % p)

def edwards_add(P, Q):
    (x1, y1, z1, t1) = P
    (x2, y2, z2, t2) = Q
    a = (y1 - x1) * (y2 - x2) % p
    b = (y1 + x1) * (y2 + x2) % p
    c = t1 * 2 * d * t2 % p
    dd = z1 * 2 * z2 % p
    e = b - a
    f = dd - c
    g = dd + c
    h = b + a
    return (e * f % p, g * h % p, f * g % p, e * h % p)

def scalarmult(P, e):
    Q = (0, 1, 1, 0)
    while e > 0:
        if e & 1:
            Q = edwards_add(Q, P)
        P = edwards_add(P, P)
        e >>= 1
    return Q

def encodepoint(P):
    (x, y, z, t) = P
    zi = inv(z)
    x = x * zi % p
    y = y * zi % p
    bits = [(y >> i) & 1 for i in range(255)] + [x & 1]
    return bytes(sum(bits[i * 8 + j] << j for j in range(8)) for i in range(32))

def publickey(sk32):
    """Clé publique Ed25519 depuis une graine privée de 32 octets."""
    h = H(sk32)
    a = int.from_bytes(h[:32], "little")
    a &= (1 << 254) - 8
    a |= (1 << 254)
    return encodepoint(scalarmult(B, a))

q = 2**252 + 27742317777372353535851937790883648493

def sign(msg, sk32):
    """Signature Ed25519 (RFC 8032) depuis une graine privee de 32 octets."""
    h = H(sk32)
    a = int.from_bytes(h[:32], "little")
    a &= (1 << 254) - 8
    a |= (1 << 254)
    A = encodepoint(scalarmult(B, a))
    r = int.from_bytes(H(h[32:] + msg), "little") % q
    R = encodepoint(scalarmult(B, r))
    k = int.from_bytes(H(R + A + msg), "little") % q
    S = (r + k * a) % q
    return R + S.to_bytes(32, "little")

B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

def bip39_seed(mnemonic, passphrase=""):
    m = unicodedata.normalize("NFKD", mnemonic)
    s = unicodedata.normalize("NFKD", "mnemonic" + passphrase)
    return hashlib.pbkdf2_hmac("sha512", m.encode(), s.encode(), 2048, 64)

def slip10_ed25519(seed, path):
    I = hmac.new(b"ed25519 seed", seed, hashlib.sha512).digest()
    kL, kR = I[:32], I[32:]
    for index in path:
        assert index & 0x80000000, "ed25519: hardened only"
        data = b"\x00" + kL + index.to_bytes(4, "big")
        I = hmac.new(kR, data, hashlib.sha512).digest()
        kL, kR = I[:32], I[32:]
    return kL

def crc16_xmodem(data):
    crc = 0
    for b in data:
        crc ^= b << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc

def b32encode_nopad(data):
    out, buf, bits = [], 0, 0
    for b in data:
        buf = (buf << 8) | b
        bits += 8
        while bits >= 5:
            out.append(B32[(buf >> (bits - 5)) & 0x1F])
            bits -= 5
    if bits:
        out.append(B32[(buf << (5 - bits)) & 0x1F])
    return "".join(out)

def strkey_account(pub32):
    payload = bytes([0x30]) + pub32
    crc = crc16_xmodem(payload)
    return b32encode_nopad(payload + bytes([crc & 0xFF, (crc >> 8) & 0xFF]))

def strkey_decode(addr):
    buf, bits, out = 0, 0, bytearray()
    for c in addr:
        buf = (buf << 5) | B32.index(c)
        bits += 5
        if bits >= 8:
            out.append((buf >> (bits - 8)) & 0xFF)
            bits -= 8
    return bytes(out)

def address(mnemonic, coin_type, account=0, passphrase=""):
    seed = bip39_seed(mnemonic, passphrase)
    H = 0x80000000
    priv = slip10_ed25519(seed, [44 | H, coin_type | H, account | H])
    return strkey_account(publickey(priv)), priv


ENVELOPE_TYPE_TX = 2
KEY_TYPE_ED25519 = 0
PRECOND_TIME = 1
MEMO_NONE, MEMO_TEXT, MEMO_ID = 0, 1, 2
OP_CREATE_ACCOUNT, OP_PAYMENT = 0, 1
ASSET_TYPE_NATIVE = 0

def u32(v): return struct.pack(">I", v)
def i64(v): return struct.pack(">q", v)
def u64(v): return struct.pack(">Q", v)

def pad4(b):
    r = (-len(b)) % 4
    return b + b"\x00" * r

def xdr_string(s):
    b = s.encode("utf-8")
    return u32(len(b)) + pad4(b)

def pubkey_bytes(addr):
    raw = strkey_decode(addr)
    assert len(raw) == 35 and raw[0] == 0x30, addr
    return raw[1:33]

def account_id(addr):
    """PublicKey union : discriminant + 32 octets."""
    return u32(KEY_TYPE_ED25519) + pubkey_bytes(addr)

def muxed_account(addr):
    """MuxedAccount union : meme encodage pour le cas ed25519 simple."""
    return u32(KEY_TYPE_ED25519) + pubkey_bytes(addr)

def memo(kind, value):
    if kind == MEMO_NONE: return u32(MEMO_NONE)
    if kind == MEMO_TEXT: return u32(MEMO_TEXT) + xdr_string(value)
    if kind == MEMO_ID:   return u32(MEMO_ID) + u64(value)
    raise ValueError(kind)

def op_payment(dest, stroops):
    return (u32(0)                      # sourceAccount optionnel : absent
            + u32(OP_PAYMENT)
            + muxed_account(dest)
            + u32(ASSET_TYPE_NATIVE)
            + i64(stroops))

def op_create_account(dest, stroops):
    return (u32(0)
            + u32(OP_CREATE_ACCOUNT)
            + account_id(dest)
            + i64(stroops))

def transaction(src, fee, seq, min_time, max_time, memo_bytes, ops):
    out = muxed_account(src) + u32(fee) + i64(seq)
    out += u32(PRECOND_TIME) + u64(min_time) + u64(max_time)
    out += memo_bytes
    out += u32(len(ops)) + b"".join(ops)
    out += u32(0)                        # ext v=0
    return out

def network_id(passphrase):
    return hashlib.sha256(passphrase.encode("utf-8")).digest()

def tx_hash(tx_xdr, passphrase):
    return hashlib.sha256(network_id(passphrase) + u32(ENVELOPE_TYPE_TX) + tx_xdr).digest()

def envelope(tx_xdr, pub32, sig64):
    return base64.b64encode(
        u32(ENVELOPE_TYPE_TX) + tx_xdr
        + u32(1) + pub32[-4:] + u32(len(sig64)) + pad4(sig64)
    ).decode()


# ═════════════════════════════════════════════════════════════════════════
# VECTEURS
# ═════════════════════════════════════════════════════════════════════════

PHRASE_ESSAI_STELLAR = "Test SDF Network ; September 2015"
PHRASE_PI = "Pi Network"

# Phrase de reference du SEP-0005, cas de test 1.
SEP5_PHRASE = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
SEP5_SEED = ("e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497e"
             "e4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186")
SEP5_ADRESSES = [
    "GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6",
    "GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX",
    "GAY5PRAHJ2HIYBYCLZXTHID6SPVELOOYH2LBPH3LD4RUMXUW3DOYTLXW",
    "GAOD5NRAEORFE34G5D4EOSKIJB6V4Z2FGPBCJNQI6MNICVITE6CSYIAE",
    "GBCUXLFLSL2JE3NWLHAWXQZN6SQC6577YMAU3M3BEMWKYPFWXBSRCWV4",
    "GBRQY5JFN5UBG5PGOSUOL4M6D7VRMAYU6WW2ZWXBMCKB7GPT3YCBU2XZ",
    "GBY27SJVFEWR3DUACNBSMJB6T4ZPR4C7ZXSTHT6GMZUDL23LAM5S2PQX",
    "GAY7T23Z34DWLSTEAUKVBPHHBUE4E3EMZBAQSLV6ZHS764U3TKUSNJOF",
    "GDJTCF62UUYSAFAVIXHPRBR4AUZV6NYJR75INVDXLLRZLZQ62S44443R",
    "GBTVYYDIYWGUQUTKX6ZMLGSZGMTESJYJKJWAATGZGITA25ZB6T5REF44",
]

# Cle de test PUBLIEE par Stellar (go/txnbuild/helpers_test.go). Elle ne
# protege rien : elle existe pour que ces vecteurs soient reproductibles.
CLE_TEST_STELLAR = "SBPQUZ6G4FZNWFHKUWC5BEYWF6R52E3SEP7R3GWYSM2XTKGF5LNTWW4R"
SOURCE = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3"
DEST_CREATION = "GCCOBXW2XQNUSL467IEILE6MMCNRR66SSVL4YQADUNYYNUVREF3FIV2Z"
DEST_PAIEMENT = "GB7BDSZU2Y27LYNLALKKALB52WS2IZWYBDGY6EQBLEED3TJOCVMZRH7H"

# Attendus du SDK Go (txnbuild/transaction_test.go : TestHashHex, TestPayment).
GO_CREATION_ENV = ("AAAAAgAAAADg3G3hclysZlFitS+s5zWyiiJD5B0STWy5LXCj6i5yxQAAAGQAIiCNAAAAGgAA"
                   "AAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAACE4N7avBtJL576CIWTzGCb"
                   "GPvSlVfMQAOjcYbSsSF2VAAAAAAF9eEAAAAAAAAAAAHqLnLFAAAAQB7MjKIwNEOTIjbEeV+Q"
                   "IjaQp/ZpV5qpbkbDaU54gkfdTOFOUxZq66lTS5FOfP5fmPIVD8InQ00Usy2SmzFC/wc=")
GO_CREATION_HASH = "1b3905ba8c3c0ecc68ae812f2d77f27c697195e8daf568740fc0f5662f65f759"
GO_PAIEMENT_ENV = ("AAAAAgAAAADg3G3hclysZlFitS+s5zWyiiJD5B0STWy5LXCj6i5yxQAAAGQAIiCNAAAAGwAA"
                   "AAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAQAAAAB+Ecs01jX14asC1KAsPdWl"
                   "pGbYCM2PEgFZCD3NLhVZmAAAAAAAAAAABfXhAAAAAAAAAAAB6i5yxQAAAEDXBkKYzThQi3/X"
                   "hJqGzfh/EjaAx/4zK3xBT1/JDNtdkk/kxn4qxHVx++xiV72lqZXxiphNwflA8C7mC8Dvim0E")


def _cle_de_test():
    brut = strkey_decode(CLE_TEST_STELLAR)
    assert brut[0] == 0x90, "octet de version d'une graine attendu : 0x90"
    priv = brut[1:33]
    return priv, publickey(priv)


def _signer(tx, phrase, priv, pub):
    h = tx_hash(tx, phrase)
    # ON SIGNE L'EMPREINTE (32 octets), jamais la charge complete.
    return h, envelope(tx, pub, sign(h, priv))


def controler():
    """Rejoue les vecteurs EXTERNES. Rend le nombre d'echecs."""
    echecs = 0

    def verifier(nom, obtenu, attendu):
        nonlocal echecs
        ok = obtenu == attendu
        print(f"  [{'ok ' if ok else 'ECHEC'}] {nom}")
        if not ok:
            echecs += 1
            print(f"         attendu : {attendu}")
            print(f"         obtenu  : {obtenu}")

    print("SEP-0005, cas de test 1 (type de piece 148, celui de Stellar)")
    verifier("graine BIP-39", bip39_seed(SEP5_PHRASE).hex(), SEP5_SEED)
    for i, attendue in enumerate(SEP5_ADRESSES):
        a, _ = address(SEP5_PHRASE, 148, i)
        verifier(f"m/44'/148'/{i}'", a, attendue)

    print("\nSDK Stellar pour Go (txnbuild)")
    priv, pub = _cle_de_test()
    verifier("adresse de la cle de test", strkey_account(pub), SOURCE)

    tx = transaction(SOURCE, 100, 9605939170639898, 0, 0,
                     memo(MEMO_NONE, None),
                     [op_create_account(DEST_CREATION, 100_000_000)])
    h, env = _signer(tx, PHRASE_ESSAI_STELLAR, priv, pub)
    verifier("empreinte, creation de compte", h.hex(), GO_CREATION_HASH)
    verifier("enveloppe, creation de compte", env, GO_CREATION_ENV)

    tx = transaction(SOURCE, 100, 9605939170639899, 0, 0,
                     memo(MEMO_NONE, None),
                     [op_payment(DEST_PAIEMENT, 100_000_000)])
    _, env = _signer(tx, PHRASE_ESSAI_STELLAR, priv, pub)
    verifier("enveloppe, paiement", env, GO_PAIEMENT_ENV)
    return echecs


def produire():
    """Affiche les vecteurs que les tests Kotlin attendent pour les chemins
    que les vecteurs officiels ne couvrent pas : mémo et date limite."""
    priv, pub = _cle_de_test()
    print("\nVecteurs produits ici (reseau Pi, frais 200, sequence "
          "9605939170639899, fin 1700000000, montant 12345678 stroops)")
    print(f"  idReseau(\"{PHRASE_PI}\") = {network_id(PHRASE_PI).hex()}")
    for nom, m in (
        ("memo texte \"VaultEx\"", memo(MEMO_TEXT, "VaultEx")),
        ("memo identifiant 314159", memo(MEMO_ID, 314159)),
        ("memo accentue \"reçu café\"", memo(MEMO_TEXT, "reçu café")),
    ):
        tx = transaction(SOURCE, 200, 9605939170639899, 0, 1700000000, m,
                         [op_payment(DEST_PAIEMENT, 12_345_678)])
        h, env = _signer(tx, PHRASE_PI, priv, pub)
        print(f"\n  {nom}")
        print(f"    empreinte : {h.hex()}")
        print(f"    enveloppe : {env}")


if __name__ == "__main__":
    echecs = controler()
    produire()
    print()
    if echecs:
        print(f"{echecs} vecteur(s) externe(s) en echec — ne pas se fier aux "
              "valeurs produites ci-dessus.")
        sys.exit(1)
    print("Tous les vecteurs externes tombent juste.")
