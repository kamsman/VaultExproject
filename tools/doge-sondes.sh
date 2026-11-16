#!/usr/bin/env bash
# ==========================================================================
# VaultEx — QUEL FOURNISSEUR DOGECOIN REPOND, ET DANS QUELLE FORME ?
# ==========================================================================
#
# UTILISATION (Git Bash, depuis la racine du projet) :
#
#   ./tools/doge-sondes.sh                      <-- commencez par celui-ci
#   ./tools/doge-sondes.sh --vivante            <-- puis celui-ci
#   ./tools/doge-sondes.sh DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L
#
# --------------------------------------------------------------------------
# CE QUE LA PREMIERE MESURE A DONNE — OUAGADOUGOU, 14 NOVEMBRE 2026
# --------------------------------------------------------------------------
#
#   blockbook (Trezor)   HTTP 403 sur les QUATRE points d'entree, en 0,8 a
#                        1,4 s, avec une page Cloudflare en reponse. Ce
#                        n'est pas une panne : c'est un refus. Le service
#                        est joignable et ne veut pas repondre.
#
#   blockcypher          OK partout, 1,2 a 1,7 s. La diffusion refuse le
#                        corps invalide en le DISANT : « Transaction
#                        missing input or output » — donc elle diffuse.
#
#   blockchair           OK, mais 2,9 s sur l'appel d'adresse, celui dont
#                        depend l'envoi. Deux fois BlockCypher.
#
# CETTE MESURE A RENVERSE UN AVIS, ET C'EST POUR CA QU'ELLE EXISTE.
# Blockbook etait le candidat PREFERE avant de mesurer : une seule forme
# d'API pour les trois besoins, et plusieurs fournisseurs payants servent
# la meme, donc changer de maison plus tard n'aurait pas touche le moteur
# d'envoi. L'argument etait bon ; il ne vaut rien sur un service qui
# refuse les requetes venant d'ici.
#
# --------------------------------------------------------------------------
# SECONDE MESURE — LE QUOTA, ET CE QU'IL TRANCHE
# --------------------------------------------------------------------------
#
#   blockcypher   x-ratelimit-remaining: 1   sur CHACUN des trois appels.
#
# A QUELLE FENETRE CE COMPTEUR SE RAPPORTE N'EST PAS ECRIT, et on ne le
# devinera pas ici. Ce qu'il dit suffit : il reste UNE requete. Un envoi
# consomme au moins deux appels — lire les sorties, diffuser — sans
# compter la lecture du solde a chaque ouverture de l'ecran. Pour deux
# cent vingt-trois utilisateurs, la fourniture gratuite de BlockCypher
# n'est pas un candidat ; c'est un bac a sable.
#
# LA RECHERCHE `--vivante` A ECHOUE DANS LA FOULEE, ce qui est coherent :
# elle ajoute trois appels a un quota deja epuise.
#
# ─── CE QUE CA NE DIT PAS : QUE BLOCKBOOK SOIT MORT ───────────────────────
#
# Le 403 mesure la politique de L'INSTANCE PUBLIQUE DE TREZOR, pas la
# forme d'API. NOWNodes sert du Blockbook sur son propre hote, derriere
# une cle — donc sans le mur anti-robot qui nous a refuses. L'argument de
# portabilite tient toujours, il demande juste un compte.
#
# --------------------------------------------------------------------------
# TROISIEME MESURE — L'HOTE NOWNODES REPOND, ET BLOCKCHAIR DECROCHE
# --------------------------------------------------------------------------
#
#   nownodes     HTTP 401 « Unknown API_key », en 1,6 s.
#
# LA CLE ETAIT FAUSSE — un exemple colle tel quel — et c'est justement ce
# qui rend la mesure utile. Un 401 n'est ni un 403 ni un delai depasse :
# l'hote est JOIGNABLE depuis le Burkina, il a lu la requete, et il a
# repondu une phrase qui a du sens. Avec une vraie cle, il repondra.
#
# C'est le seul candidat de forme Blockbook qui nous parle. Il reste donc
# a mesurer son quota, et lui seul.
#
#   blockchair   DELAI DEPASSE a 20 s sur l'appel d'adresse, apres 2,9 s
#                puis 1,9 s aux deux mesures precedentes. Le meme appel,
#                le meme jour.
#
# TROIS MESURES, TROIS RESULTATS DIFFERENTS : c'est pire qu'une panne
# franche. Une panne franche se voit et se contourne ; une lenteur
# intermittente produit des envois qui echouent une fois sur trois, sans
# motif lisible, chez des gens qui viennent de signer une transaction.
# Blockchair sort de la liste tant qu'une mesure repetee ne le rehabilite
# pas.
#
# ─── NOWNODES EST HORS JEU ────────────────────────────────────────────────
#
# MESURE, 16 NOVEMBRE : le compte s'ouvre, mais le tableau de bord et la
# cle restent desactives tant qu'on n'a pas paye. Leur « trial » ne
# delivre pas de cle.
#
# Ce n'est donc plus une question technique. On ne reviendra chez eux que
# si la decision de payer est prise, et elle n'appartient pas a ce
# fichier.
#
# --------------------------------------------------------------------------
# QUATRIEME MESURE — TREZOR EST UNE POLITIQUE, PAS UNE MACHINE
# --------------------------------------------------------------------------
#
#   doge.trezor.io, doge2, doge5 : HTTP 403 sur les TROIS, en 0,2 a 0,4 s.
#
# L'hypothese etait que le 403 mesurait la politique d'UNE machine. Trois
# hotes, trois refus : c'est la politique de la maison. Blockbook servi
# par Trezor est raye, definitivement, et avec lui l'idee d'obtenir cette
# forme d'API sans compte.
#
#   dogechain.info, chain.so : HTTP 301 — ET C'ETAIT MA FAUTE.
#
# Pas un echec de leur part : une redirection que `curl` ne suivait pas,
# faute de `-L`. J'allais rayer deux candidats pour une option manquante
# dans l'outil cense les juger. Corrige ; ils sont a remesurer.
#
#   blockchair : OK en 1,2 s cette fois — apres 2,9 s, 1,9 s, puis un
#                delai depasse a 20 s. Quatre mesures, quatre resultats.
#                L'intermittence est confirmee, pas levee.
#
# --------------------------------------------------------------------------
# CINQUIEME MESURE — LE JETON BLOCKCYPHER MULTIPLIE LE QUOTA PAR CENT
# --------------------------------------------------------------------------
#
#   anonyme        x-ratelimit-remaining: 1
#   avec jeton     x-ratelimit-remaining: 99, puis 99, puis 98
#
# Le compteur descend d'une unite par requete : la fenetre contient donc
# une centaine d'appels, la ou l'anonyme en laissait un. C'est mesure, et
# non lu dans une documentation.
#
# CENT APPELS PAR FENETRE, EST-CE ASSEZ ? Ca ne se decide pas ici, mais
# l'ordre de grandeur se pose : un envoi coute au moins deux appels —
# lire les sorties, diffuser. Cent appels tiennent donc une cinquantaine
# d'envois, a condition que RIEN d'autre n'interroge le service. Afficher
# un solde Dogecoin a chaque ouverture de l'ecran, pour 223 personnes, le
# viderait avant midi.
#
# La conclusion technique est donc : utilisable pour l'ENVOI, pas pour la
# consultation permanente. Si le solde doit s'afficher en continu, il
# faudra soit un service payant, soit un cache cote relais — ce dernier
# etant deja ce que fait le Worker pour les cours.
#
# --------------------------------------------------------------------------
# SIXIEME MESURE — DOGECHAIN REPOND, SANS CLE, ET ANNONCE SON PLAFOND
# --------------------------------------------------------------------------
#
#   dogechain / sorties   OK en 1,9 s, « unspent_outputs » rendu
#   dogechain / solde     OK en 1,2 s
#   en-tetes              ratelimit-limit: 120, ratelimit-remaining: 29
#   arrivee               https://dogechain.COM/... (le .info redirige)
#
# C'est le premier candidat qui rende les sorties non depensees sans
# compte, sans jeton et sans paiement. Il annonce lui-meme son plafond, ce
# qu'aucun autre n'a fait.
#
# DEUX CHOSES A VERIFIER AVANT DE LE RETENIR. Sait-il DIFFUSER — lire les
# sorties ne sert a rien si l'on ne peut pas pousser la transaction. Et que
# vaut ce « remaining: 29 sur 120 » : il etait deja a 29 avant tout appel
# de cette sonde, donc le compteur est partage, ou la fenetre est longue.
# Deux relances espacees trancheront.
#
#   sochain   HTTP 404, et son propre message l'explique : « API v2 was
#             retired. Every v2 call has a v3 replacement ». Ce n'etait pas
#             une panne, c'etait mon adresse. La v3 est maintenant sondee.
#
#   blockcypher avec jeton : 96 puis 95 sur les lectures, mais 99 sur la
#             diffusion — les deux points d'entree ont donc des compteurs
#             SEPARES. Bonne nouvelle pour un portefeuille, dont les
#             lectures sont bien plus nombreuses que les envois.
#
# --------------------------------------------------------------------------
# SEPTIEME MESURE — SOCHAIN V3 ANNONCE VINGT FOIS LE QUOTA DES AUTRES
# --------------------------------------------------------------------------
#
#   sochain v3   OK en 1,4 s, « outputs » rendu
#                x-ratelimit-limit: 2500, remaining: 2499, reset: 23841
#
# Deux mille cinq cents requetes par fenetre, contre 120 chez dogechain et
# une centaine chez BlockCypher avec un compte. Sans cle, et il rend les
# sorties non depensees. C'est le meilleur chiffre mesure jusqu'ici.
#
# LE « reset: 23841 » EST A CONFIRMER : environ six heures et demie. Si
# c'est la duree de la fenetre, cela fait pres de quatre cents requetes par
# heure ; si c'est le temps restant d'une fenetre quotidienne, c'est 2500
# par jour. Deux relances espacees le diront, et la difference compte.
#
#   dogechain / diffusion   HTTP 405 — ET CE N'EST PAS UN REFUS.
#
# Un 405 dit « pas comme ca », pas « je ne sais pas faire ». Le point
# d'entree existe et n'accepte simplement pas un corps JSON. Conclure a
# l'incapacite sur ce code aurait raye le seul service sans cle qui
# reponde en moins d'une seconde. La sonde essaie donc les deux formes, et
# METHODE est desormais un etat distinct de REJET.
#
#   dogechain / remaining   29 sur 120, inchange sur QUATRE appels
#                           d'affilee. Le compteur ne descend donc pas par
#                           requete : soit il est partage, soit l'en-tete
#                           ne dit pas ce qu'on croit. A ne pas prendre
#                           pour argent comptant.
#
# --------------------------------------------------------------------------
# HUITIEME MESURE — LA REPONSE, ET ELLE N'EST PAS CELLE QU'ON CHERCHAIT
# --------------------------------------------------------------------------
#
#   sochain / diffusion      HTTP 404 : « Broadcasting transactions is not
#                            available. » Explicite. Sochain LIT, il ne
#                            pousse pas.
#   dogechain / diffusion    405 sur les DEUX formes, JSON et formulaire.
#                            Le chemin n'est pas le bon ; peu importe
#                            desormais.
#   blockcypher / diffusion  REJET, avec un message qui nomme le defaut du
#                            corps envoye. SEUL SERVICE MESURE QUI DIFFUSE.
#
# ON CHERCHAIT UN FOURNISSEUR ; IL EN FAUT DEUX, ET C'EST MIEUX.
#
# Aucun service gratuit ne fait les deux. Mais les deux besoins n'ont rien
# en commun : lire se fait souvent — a chaque ouverture d'ecran, a chaque
# preparation d'envoi — tandis que diffuser se fait UNE fois par envoi.
# Les confier au meme service, c'est faire payer a la diffusion le quota
# que les lectures consomment.
#
#   LIRE      sochain v3     2500 par fenetre, sans cle, 0,9 s
#   DIFFUSER  blockcypher    compteur SEPARE de ses lectures, 0,6 s
#
# Le compteur separe de BlockCypher, mesure au tour precedent — 96 et 95
# sur les lectures pendant que la diffusion restait a 99 — devient ici
# l'argument central : on ne consommera JAMAIS son quota de lecture,
# puisqu'on ne lira pas chez lui.
#
# LA FENETRE DE SOCHAIN EST UN DECOMPTE. reset est passe de 23841 a 22597
# entre deux mesures espacees d'environ vingt minutes, et remaining de
# 2499 a 2498. C'est donc une fenetre fixe d'environ six heures et demie,
# qui se vide a la requete — pres de quatre cents lectures par heure. Un
# portefeuille n'en demande pas le dixieme.
#
# ─── CE QU'IL RESTE A VERIFIER, ET CA NE SE VERIFIE QU'EN VRAI ────────────
#
# Que BlockCypher accepte une transaction Dogecoin VALIDE. Il refuse
# proprement un corps invalide, ce qui prouve qu'il analyse — pas qu'il
# diffuse. Seul un envoi reel le dira, avec un montant qu'on accepte de
# perdre, le jour ou le moteur d'envoi existera.
#
# --------------------------------------------------------------------------
# NEUVIEME MESURE — SOCHAIN NE SAIT MONTRER QUE DIX PIECES
# --------------------------------------------------------------------------
#
# L'adresse DBx8K8iX… detient 5 907 762,898 DOGE — dogechain le dit en un
# appel. Sochain en rend DIX sorties de 0,01, soit 0,10 DOGE. Et « page=2 »
# rend EXACTEMENT les memes dix : le parametre est ignore.
#
# UN PORTEFEUILLE ECRIT CONTRE CETTE REPONSE AURAIT AFFICHE 0,10 DOGE a
# quelqu'un qui en detient six millions, et refuse tout envoi depassant dix
# centiemes. Rien dans la reponse ne le signale : « status: success », dix
# lignes, et c'est tout.
#
# Sochain sort donc, malgre son quota de 2500 — le meilleur mesure. Un
# quota genereux sur une reponse tronquee ne vaut rien.
#
#   dogechain   la pagination MARCHE : /unspent/<adresse>/2 rend d'autres
#               hachages. Et /address/balance/<adresse> rend le solde
#               TOTAL en un seul appel.
#
# ─── CE QUE CA CHANGE DANS L'ARCHITECTURE ────────────────────────────────
#
#   SOLDE      dogechain /address/balance   — UN appel, a chaque ecran
#   PIECES     dogechain /address/unspent   — pagine, SEULEMENT a l'envoi
#   DIFFUSER   blockcypher                  — seul service qui pousse
#
# Separer le solde des pieces n'est pas une elegance : c'est ce qui permet
# a un portefeuille de cinquante pieces de ne pas faire cinq appels a
# chaque ouverture d'ecran. On ne parcourt les pages qu'au moment ou
# quelqu'un envoie vraiment, et seulement jusqu'a couvrir le montant.
#
# ─── ET UNE GARDE QUE CETTE MESURE IMPOSE ────────────────────────────────
#
# Sochain a ignore « page » EN SILENCE. Un parcours qui fait confiance au
# numero de page tournerait indefiniment sur un service qui se comporte
# ainsi. Le lecteur devra donc s'arreter des qu'une page rend un hachage
# deja vu — pas seulement quand elle rend zero.
#
# L'argument est une adresse Dogecoin a interroger. Sans lui, la sonde
# emploie l'adresse du vecteur de test de DogeWalletTest, qui est VIDE.
#
# UNE ADRESSE VIDE NE PROUVE PAS LA MOITIE DE CE QU'ON CHERCHE. Elle dit
# « le service repond » ; elle ne dit pas quels champs porte une sortie non
# depensee, puisqu'il n'y en a aucune. Pour la seconde moitie, donnez une
# adresse qui DETIENT des DOGE — n'importe laquelle, copiee depuis un bloc
# recent sur un explorateur public. Elle n'a pas a etre la votre : on ne
# fait que lire.
#
# --------------------------------------------------------------------------
# POURQUOI CETTE SONDE EXISTE, ET POURQUOI ELLE TOURNE CHEZ VOUS
# --------------------------------------------------------------------------
#
# Envoyer du Dogecoin demande trois choses qu'aucune bibliotheque ne donne :
# lire les sorties non depensees d'une adresse, estimer des frais, et
# diffuser la transaction signee. Il faut donc un fournisseur, et celui de
# Bitcoin n'en est pas un — il ne sert pas cette chaine.
#
# LE CHOIX NE SE FAIT PAS SUR UNE PAGE DE DOCUMENTATION. Ce depot porte deja
# deux contre-exemples : `api.binance.com` repond 403 a un Worker
# Cloudflare et parfaitement a un navigateur, et sept noeuds BSC refusent
# `eth_getLogs` depuis cette meme IP en l'acceptant ailleurs. Maven Central
# a refuse ce conteneur le jour ou ces lignes ont ete ecrites.
#
# La seule mesure qui decrive ce que vivra l'application est celle faite
# depuis le reseau ou elle tourne. C'est pour ca que ce script s'execute sur
# VOTRE machine, au Burkina Faso, et pas la ou il a ete ecrit.
#
# --------------------------------------------------------------------------
# CE QU'ELLE MESURE, ET CE QU'ELLE NE MESURE PAS
# --------------------------------------------------------------------------
#
# ELLE MESURE : qui repond, en combien de temps, et si la reponse porte les
# champs dont un envoi a besoin. Les noms de champs sont AFFICHES plutot que
# supposes — si l'un d'eux s'appelle autrement, le tri ne leverait aucune
# erreur, il rendrait « aucune sortie trouvee » sur une adresse pleine, et
# on chercherait le defaut du cote de la chaine pendant des heures.
#
# ELLE NE MESURE PAS la diffusion. Pousser une transaction demande une
# transaction signee, donc le moteur d'envoi qui n'existe pas encore. La
# sonde se contente de dire si le point d'entree de diffusion EXISTE, en
# l'appelant avec un corps volontairement invalide : un service qui repond
# « transaction invalide » est un service qui diffuse, et c'est tout ce
# qu'on peut savoir a ce stade. AUCUNE transaction reelle n'est envoyee.
#
# ELLE NE DIT RIEN DES TARIFS NI DES QUOTAS. Ils changent, ils dependent du
# compte, et les lire ici reviendrait a les inventer. Allez les lire chez le
# fournisseur que la mesure aura retenu.
#
# --------------------------------------------------------------------------
# LES CLES
# --------------------------------------------------------------------------
#
# Seuls les services interrogeables SANS cle sont sondes par defaut : on ne
# veut pas ouvrir un compte chez quatre fournisseurs pour en choisir un.
# Ceux qui en exigent une sont nommes a la fin, sans etre appeles.
#
# Si vous avez deja une cle, donnez-la par l'environnement et la sonde
# l'emploiera :
#
#   NOWNODES_CLE=<collez-ici-la-cle-NOWNodes> ./tools/doge-sondes.sh
#
# LE CHEVRON FAIT PARTIE DU RAPPEL, PAS DE LA COMMANDE. Un exemple ecrit
# « xxxx » ou « ta_cle » ressemble assez a un jeton pour etre colle tel
# quel — c'est arrive, et le service a repondu « Unknown API_key ».
#
# ==========================================================================

set -uo pipefail

ADRESSE_TEST="DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC"
ADRESSE="${1:-$ADRESSE_TEST}"
DELAI=20
ENTETES=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/dogesondes.$$")
trap 'rm -f "$ENTETES"' EXIT

# --------------------------------------------------------------------------
# TROUVER UNE ADRESSE VIVANTE PLUTOT QUE D'EN DEMANDER UNE
# --------------------------------------------------------------------------
#
# L'adresse de test est vide, et une adresse vide ne montre AUCUNE sortie
# non depensee — donc pas les noms de champs, qui sont precisement ce qu'on
# vient verifier. Demander a l'utilisateur d'aller en chercher une sur un
# explorateur marche, mais ajoute une corvee a chaque relance.
#
# `--vivante` remonte donc la chaine : dernier bloc, sa premiere
# transaction, l'adresse de sa premiere sortie. Trois appels de plus, d'ou
# le fait que ce ne soit PAS le comportement par defaut : sur une fourniture
# gratuite, chaque appel compte, et la premiere question — « qui repond ? »
# — se tranche sans eux.
#
# L'adresse trouvee est AFFICHEE : notez-la et passez-la en argument aux
# relances suivantes, ce qui economise les trois appels.
if [ "${1:-}" = "--vivante" ]; then
  echo
  echo "  Recherche d'une adresse approvisionnee dans le dernier bloc…"

  # --------------------------------------------------------------------
  # UNE RECHERCHE QUI ECHOUE DOIT DIRE A QUELLE ETAPE, ET POURQUOI
  # --------------------------------------------------------------------
  #
  # La premiere version avalait tout : `2>/dev/null`, aucun code HTTP
  # regarde, et un seul message a la fin — « echec de la recherche ».
  # MESURE : elle a echoue chez l'utilisateur, et cette sortie ne
  # permettait pas de distinguer un quota epuise d'un champ renomme.
  #
  # C'est la faute meme que ce script reproche aux fournisseurs : rendre
  # « rien trouve » la ou il faudrait rendre « je n'ai pas pu regarder ».
  # Chaque etape affiche donc son code et le debut de sa reponse.
  # --------------------------------------------------------------------
  # LA REPONSE EST APLATIE AVANT D'ETRE LUE, ET C'EST INDISPENSABLE
  # --------------------------------------------------------------------
  #
  # MESURE : l'etape « bloc » rendait 200 et la recherche s'arretait la.
  # Ni quota, ni champ renomme — `sed` travaille LIGNE PAR LIGNE, et
  # BlockCypher formate ses reponses sur plusieurs lignes. Le nom du
  # champ « txids » et le premier identifiant n'etaient pas sur la meme
  # ligne, donc aucun motif ne pouvait les relier.
  #
  # C'est le diagnostic ajoute au tour precedent qui l'a montre : sans
  # les codes par etape, on aurait soupconne le quota.
  #
  # SECONDE FAUTE, EMPILEE SUR LA PREMIERE : apres aplatissement, le
  # motif exigeait `["` colles. BlockCypher ecrit `[` puis des espaces
  # puis le guillemet. D'ou `\[[^"]*"`.
  DERNIER=""
  etape() {
    local titre="$1" url="$2" rep code
    rep=$(curl -sS -m "$DELAI" -w '\n%{http_code}' "$url" 2>&1)
    code=$(printf '%s' "$rep" | tail -1)
    DERNIER=$(printf '%s' "$rep" | sed '$d' | tr -d '\n')
    printf '    %-14s %-7s %s\n' "$titre" "$code" "$(printf '%s' "$DERNIER" | cut -c1-70)"
    [ "$code" = "200" ]
  }

  trouvee=""
  if etape "dernier bloc" "https://api.blockcypher.com/v1/doge/main"; then
    hash=$(sed -n 's/.*"hash"[[:space:]]*:[[:space:]]*"\([0-9a-f]\{64\}\)".*/\1/p' <<<"$DERNIER" | head -1)
    if [ -n "$hash" ] && etape "bloc" "https://api.blockcypher.com/v1/doge/main/blocks/$hash"; then
      # ------------------------------------------------------------
      # LA PREMIERE TRANSACTION D'UN BLOC EST TOUJOURS LA COINBASE
      # ------------------------------------------------------------
      #
      # MESURE : le vecteur rapporte etait une coinbase — entree au
      # prevout nul, index ffffffff, sortie en pay-to-pubkey, frais a
      # zero. Parfait pour verifier la SERIALISATION, inutile comme
      # modele de depense : rien de ce qu'un portefeuille signe ne lui
      # ressemble.
      #
      # On prend donc la SECONDE, qui est une transaction ordinaire.
      # `head -2 | tail -1` plutot que `head -1`, et un repli sur la
      # premiere si le bloc n'en contient qu'une — ca arrive sur un
      # bloc vide, et mieux vaut un vecteur imparfait que pas de
      # vecteur.
      # ON COUPE AVANT DE CHERCHER. Le `hash` DU BLOC est lui aussi
      # soixante-quatre caracteres hexadecimaux, et il arrive avant le
      # tableau : chercher dans toute la reponse le ramenait en tete, et
      # « la seconde entree » redevenait la coinbase. Mon propre essai
      # l'a montre avant que ca parte chez l'utilisateur.
      liste=${DERNIER#*\"txids\"}
      tous=$(sed 's/"/\n/g' <<<"$liste" | grep -E '^[0-9a-f]{64}$' | head -2)
      txid=$(printf '%s\n' "$tous" | sed -n '2p')
      [ -z "$txid" ] && txid=$(printf '%s\n' "$tous" | sed -n '1p')
      if [ -n "$txid" ] && etape "transaction" "https://api.blockcypher.com/v1/doge/main/txs/$txid"; then
        trouvee=$(sed -n 's/.*"addresses"[^]]*\[[^"]*"\(D[1-9A-HJ-NP-Za-km-z]\{25,33\}\)".*/\1/p' <<<"$DERNIER" | head -1)
      fi
    fi
  fi

  if [ -n "$trouvee" ]; then
    ADRESSE="$trouvee"
    echo "  Trouvee : $ADRESSE"
    echo "  (passez-la en argument aux prochaines relances pour economiser"
    echo "   les trois appels de recherche)"

    # ------------------------------------------------------------------
    # UNE TRANSACTION REELLE, GARDEE SUR DISQUE
    # ------------------------------------------------------------------
    #
    # Construire et signer une transaction Dogecoin ne demande AUCUN
    # fournisseur : c'est de la serialisation et de la signature. Mais
    # l'ecrire sans rien contre quoi la verifier, c'est ecrire une
    # supposition de quatre cents lignes.
    #
    # Une transaction reelle, avec son hexadecimal, est le vecteur de
    # test qui manque : on reconstruit ses octets a partir de ses
    # champs, et on compare. Si un seul octet differe — un entier ecrit
    # a l'envers, une longueur sur le mauvais nombre d'octets — ca se
    # voit immediatement, et c'est exactement ce que le reseau refuserait
    # en silence.
    #
    # LE FICHIER NE CONTIENT RIEN DE PRIVE : une transaction deja
    # publiee, et une adresse prise dans un bloc public. On ne fait que
    # lire ce que toute la chaine expose.
    MESURES="doge-mesures.json"
    if etape "tx + hex" "https://api.blockcypher.com/v1/doge/main/txs/$txid?includeHex=true"; then
      printf '%s' "$DERNIER" > "$MESURES"
      echo "  Transaction reelle enregistree dans $MESURES"
      echo "  ($(wc -c < "$MESURES") octets — envoyez-moi ce fichier)"
    fi
  else
    echo
    echo "  Recherche infructueuse — on retombe sur l'adresse de test, vide."
    echo "  Les codes ci-dessus disent OU ca s'est arrete. Un 429 signifie"
    echo "  quota epuise, pas « champ introuvable » : attendez et relancez."
    echo "  Sinon, donnez simplement une adresse en argument."
    ADRESSE="$ADRESSE_TEST"
  fi
fi

# --------------------------------------------------------------------------
# UNE ADRESSE QUI N'EN EST PAS UNE DOIT ARRETER LA SONDE
# --------------------------------------------------------------------------
#
# MESURE : un « ~ » colle par le terminal a transforme `--vivante` en
# `--vivante~`. L'argument n'etant plus reconnu, il est devenu l'adresse a
# interroger — et la sonde a consciencieusement demande aux fournisseurs
# des nouvelles d'une adresse nommee « --vivante~ ».
#
# Ils ont repondu ce qu'il fallait : « wallet not found », « Invalid
# request ». Mais dans la colonne, ca se lit comme un echec DU SERVICE,
# et c'est la pire sortie possible — une sonde qui accuse autrui de sa
# propre faute de saisie.
#
# Une adresse Dogecoin commence par D, fait 34 caracteres, et n'emploie
# ni 0 ni O ni I ni l. Ce controle ne vaut pas une somme de controle ; il
# vaut contre une faute de frappe, et c'est de ca qu'il s'agit ici.
if ! printf '%s' "$ADRESSE" | grep -qE '^D[1-9A-HJ-NP-Za-km-z]{25,33}$'; then
  echo
  echo "  « $ADRESSE » n'est pas une adresse Dogecoin."
  echo
  echo "  Une adresse Dogecoin commence par D et fait 34 caracteres."
  echo "  Si vous vouliez passer une option, verifiez qu'aucun caractere"
  echo "  ne s'y est colle : certains terminaux ajoutent un « ~ » en fin"
  echo "  de collage, et « --vivante~ » n'est plus « --vivante »."
  echo
  echo "  Rien n'a ete mesure : interroger les fournisseurs avec ceci"
  echo "  ferait passer leurs refus pour des pannes." >&2
  exit 1
fi

echo
echo "  Adresse interrogee : $ADRESSE"
if [ "$ADRESSE" = "$ADRESSE_TEST" ]; then
  echo "  (adresse de test, VIDE — relancez avec --vivante, ou avec une"
  echo "   adresse qui detient des DOGE, pour voir la forme reelle d'une"
  echo "   sortie non depensee)"
fi
echo

# --------------------------------------------------------------------------
# UNE REQUETE, SON TEMPS, ET LE DEBUT DE SA REPONSE
# --------------------------------------------------------------------------
#
# `curl -w` donne le code HTTP et la duree sans processus supplementaire.
# La reponse est TRONQUEE a l'affichage : certains catalogues rendent des
# centaines de kilo-octets, et noyer la sortie la rend illisible — or une
# sonde qu'on ne lit pas ne sert a rien.
sonder() {
  local nom="$1" url="$2" methode="${3:-GET}" corps="${4:-}"
  local sortie code duree arrivee
  : > "$ENTETES"
  # --------------------------------------------------------------------
  # LES REDIRECTIONS SE SUIVENT, SINON ON RAYE UN CANDIDAT POUR RIEN
  # --------------------------------------------------------------------
  #
  # MESURE : dogechain.info et chain.so ont rendu « 301 Moved
  # Permanently ». Sans `-L`, curl s'arrete la et la colonne affiche un
  # code d'echec — on allait rayer deux services qui repondaient
  # peut-etre parfaitement une redirection plus loin.
  #
  # C'est la faute que cette sonde existe pour eviter, commise par la
  # sonde elle-meme : rendre un verdict sur un service a partir d'une
  # limite de l'outil qui le mesure.
  #
  # `%{url_effective}` est affiche quand l'arrivee differe du depart. Une
  # redirection vers « https:// » ou vers un autre hote est une
  # information a garder : c'est l'adresse qu'il faudra ecrire dans
  # l'application, pas celle d'ou l'on est parti.
  if [ "$methode" = "FORM" ]; then
    # --------------------------------------------------------------
    # TOUT LE MONDE N'ATTEND PAS DU JSON
    # --------------------------------------------------------------
    #
    # MESURE : dogechain a rendu 405 « Method Not Allowed » sur sa
    # diffusion. Un 405 ne dit pas « je ne sais pas diffuser » — il dit
    # « pas comme ca ». Leur point d'entree attend un formulaire, pas un
    # corps JSON, et conclure a l'incapacite sur ce code aurait raye le
    # seul service sans cle qui reponde vite.
    sortie=$(curl -sSL -m "$DELAI" -D "$ENTETES" -w '\n%{http_code} %{time_total} %{url_effective}' \
             --data-urlencode "$corps" "$url" 2>&1)
  elif [ "$methode" = "POST" ]; then
    sortie=$(curl -sSL -m "$DELAI" -D "$ENTETES" -w '\n%{http_code} %{time_total} %{url_effective}' \
             -H 'content-type: application/json' -d "$corps" "$url" 2>&1)
  else
    sortie=$(curl -sSL -m "$DELAI" -D "$ENTETES" -w '\n%{http_code} %{time_total} %{url_effective}' "$url" 2>&1)
  fi
  code=$(printf '%s' "$sortie" | tail -1 | cut -d' ' -f1)
  # TRONQUEE AU CARACTERE, ET NON ARRONDIE PAR printf : « %.2f » depend de
  # LC_NUMERIC, et sur une machine en francais il attend une virgule — la
  # colonne se briserait la ou le script est justement cense tourner.
  duree=$(printf '%s' "$sortie" | tail -1 | cut -d' ' -f2 | cut -c1-5)
  arrivee=$(printf '%s' "$sortie" | tail -1 | cut -d' ' -f3-)
  local contenu etat temps
  contenu=$(printf '%s' "$sortie" | sed '$d')
  temps="${duree}s"

  case "$code" in
    200|201) etat="OK" ;;
    400|422) etat="REJET" ;;   # attendu, et souhaite, sur la diffusion
    405)     etat="METHODE" ;; # le point d'entree existe, la forme est fausse
    000)     etat="MUET"; temps="—" ;;
    *)       etat="HTTP$code" ;;
  esac
  printf '  %-34s %-8s %7s  %s\n' \
    "$nom" "$etat" "$temps" "$(printf '%s' "$contenu" | tr -d '\n' | cut -c1-100)"
  if [ -n "$arrivee" ] && [ "$arrivee" != "$url" ]; then
    printf '        arrivee : %s\n' "$arrivee"
  fi

  # --------------------------------------------------------------------
  # LE QUOTA SE LIT DANS LES EN-TETES, ET C'EST LA QUESTION DECISIVE
  # --------------------------------------------------------------------
  #
  # Un service qui repond vite et juste aujourd'hui, a une requete, ne dit
  # rien de ce qu'il fera a deux cent vingt-trois utilisateurs. Les
  # fournitures gratuites plafonnent, et le plafond ne s'annonce pas dans
  # le corps de la reponse : il est dans les en-tetes, et il n'y est que
  # quand le service veut bien le dire.
  #
  # ON NE RECOPIE PAS UN CHIFFRE LU DANS UNE DOCUMENTATION. Les quotas
  # changent, dependent du compte et du pays. Celui-ci vient du service
  # lui-meme, a l'instant ou on l'interroge.
  local quota
  quota=$(tr -d '\r' < "$ENTETES" | grep -i '^x-ratelimit\|^ratelimit\|^retry-after' |
          sed 's/^/        /' )
  [ -n "$quota" ] && printf '%s\n' "$quota"
}

# --------------------------------------------------------------------------
# LES CANDIDATS SANS CLE
# --------------------------------------------------------------------------
#
# Blockbook est l'implementation que Trezor publie et fait tourner pour
# plusieurs chaines. Son interet ici : une SEULE forme d'API couvre les
# sorties non depensees, les frais et la diffusion, et c'est la meme forme
# que proposent plusieurs fournisseurs payants — donc changer de maison plus
# tard ne voudrait pas dire reecrire le moteur d'envoi.
echo "── Blockbook (Trezor) ────────────────────────────────────────────────"
sonder "blockbook / sorties non depensees" "https://doge1.trezor.io/api/v2/utxo/$ADRESSE"
sonder "blockbook / adresse"               "https://doge1.trezor.io/api/v2/address/$ADRESSE"
sonder "blockbook / frais 6 blocs"         "https://doge1.trezor.io/api/v2/estimatefee/6"
sonder "blockbook / diffusion (invalide)"  "https://doge1.trezor.io/api/v2/sendtx/00"
echo

# --------------------------------------------------------------------------
# LE JETON BLOCKCYPHER EST GRATUIT, ET C'EST LA PISTE LA MOINS RISQUEE
# --------------------------------------------------------------------------
#
# BlockCypher est le seul service mesure qui reponde VITE et JUSTE depuis
# ici, sur les trois besoins. Son seul defaut est le quota ANONYME — une
# requete restante — et ce quota n'est pas celui d'un compte.
#
# On connait deja sa joignabilite, ce qui en fait le candidat le moins
# risque : il ne reste qu'un chiffre a verifier, la ou les autres
# demandent de tout remesurer.
#
#   BLOCKCYPHER_JETON=<le-jeton-de-votre-tableau-de-bord> \
#     ./tools/doge-sondes.sh --vivante
#
# Sans jeton, les appels partent en anonyme, exactement comme avant.
# Deux formes, parce que le separateur depend de l'URL : « & » quand elle
# porte deja des parametres, « ? » quand elle n'en a aucun. Coller un « & »
# sur une URL nue donne un parametre que le service ignore — ou refuse.
JETON_BC_ET=""   # a coller sur une URL qui a deja un « ? »
JETON_BC_QM=""   # a coller sur une URL qui n'en a pas
if [ -n "${BLOCKCYPHER_JETON:-}" ]; then
  JETON_BC_ET="&token=${BLOCKCYPHER_JETON}"
  JETON_BC_QM="?token=${BLOCKCYPHER_JETON}"
fi

echo "── BlockCypher ───────────────────────────────────────────────────────"
[ -n "$JETON_BC_ET" ] && echo "  (jeton fourni)" || echo "  (anonyme — quota minimal)"
sonder "blockcypher / sorties"  "https://api.blockcypher.com/v1/doge/main/addrs/$ADRESSE?unspentOnly=true&includeScript=true$JETON_BC_ET"
sonder "blockcypher / chaine"   "https://api.blockcypher.com/v1/doge/main$JETON_BC_QM"
sonder "blockcypher / diffusion (invalide)" \
       "https://api.blockcypher.com/v1/doge/main/txs/push$JETON_BC_QM" POST '{"tx":"00"}'
echo

# --------------------------------------------------------------------------
# TROIS AUTRES SERVICES SANS CLE, DONT LA FORME EST A CONFIRMER
# --------------------------------------------------------------------------
#
# Ils n'avaient pas ete essayes. Leurs points d'entree sont ecrits ici
# d'apres ce qu'ils publient, et c'est precisement ce que la sonde est la
# pour verifier : si l'un rend 404, c'est l'adresse qui est fausse, pas le
# service qui est mort. Lisez le code ET le debut de la reponse.
#
# LES AUTRES HOTES DE TREZOR valent le coup : le 403 mesure la politique
# d'UNE machine, et rien ne dit que toutes la partagent.
echo "── Autres hotes Blockbook de Trezor ──────────────────────────────────"
sonder "doge2.trezor / sorties" "https://doge2.trezor.io/api/v2/utxo/$ADRESSE"
sonder "doge5.trezor / sorties" "https://doge5.trezor.io/api/v2/utxo/$ADRESSE"
echo

# --------------------------------------------------------------------------
# DOGECHAIN.COM, ET NON .INFO : L'ADRESSE D'ARRIVEE EST CELLE QUI COMPTE
# --------------------------------------------------------------------------
#
# MESURE : `dogechain.info` rend 301 vers `dogechain.com`. Suivre la
# redirection marche, mais la payer a chaque appel serait absurde dans une
# application — c'est l'adresse d'ARRIVEE qu'on ecrit dans le code, jamais
# celle du depart.
#
# C'est exactement pourquoi la sonde affiche `url_effective` : sans elle on
# aurait recopie le `.info` dans l'application, et chaque envoi aurait paye
# un aller-retour de plus sur une connexion lente.
#
# LA DIFFUSION EST LA DERNIERE INCONNUE DE CE CANDIDAT. Lire les sorties ne
# sert a rien si l'on ne peut pas pousser la transaction signee.
echo "── Services independants ─────────────────────────────────────────────"
sonder "dogechain / sorties"    "https://dogechain.com/api/v1/address/unspent/$ADRESSE"
sonder "dogechain / solde"      "https://dogechain.com/api/v1/address/balance/$ADRESSE"
sonder "dogechain / diffusion (JSON)" \
       "https://dogechain.com/api/v1/pushtx" POST '{"tx":"00"}'
sonder "dogechain / diffusion (formulaire)" \
       "https://dogechain.com/api/v1/pushtx" FORM 'tx=00'
# `unspent` d'une adresse, autre forme publiee par la meme maison : si les
# deux repondent, on prendra la plus bavarde — celle qui porte le script
# de verrouillage, dont la signature a besoin.
sonder "dogechain / tx d une adresse" "https://dogechain.com/api/v1/address/transactions/$ADRESSE/1"

# SOCHAIN A RETIRE SA V2, et c'est son propre message qui le dit :
# « API v2 was retired. Every v2 call has a v3 replacement ». On sonde
# donc la v3 — qui demande peut-etre une cle, et le code le dira.
sonder "sochain v3 / sorties"   "https://sochain.com/api/v3/unspent_outputs/DOGE/$ADRESSE"
# LA DIFFUSION EST LA DERNIERE INCONNUE DU MEILLEUR CANDIDAT. Sochain
# annonce 2500 requetes par fenetre — vingt fois dogechain — et rend les
# sorties. S'il diffuse aussi, la question du fournisseur est reglee.
sonder "sochain v3 / diffusion" \
       "https://sochain.com/api/v3/broadcast_transaction/DOGE" POST '{"tx_hex":"00"}'
echo

echo "── Blockchair ────────────────────────────────────────────────────────"
sonder "blockchair / adresse"   "https://api.blockchair.com/dogecoin/dashboards/address/$ADRESSE?limit=5"
sonder "blockchair / etat"      "https://api.blockchair.com/dogecoin/stats"
echo

# --------------------------------------------------------------------------
# CE QUI DEMANDE UNE CLE
# --------------------------------------------------------------------------
if [ -n "${NOWNODES_CLE:-}" ]; then
  echo "── NOWNodes (cle fournie) ────────────────────────────────────────────"
  sonder "nownodes / sorties" \
    "https://dogebook.nownodes.io/api/v2/utxo/$ADRESSE?api_key=${NOWNODES_CLE}"
  echo
else
  echo "── Non sondes ────────────────────────────────────────────────────────"
  echo "  NOWNodes : le compte s'ouvre, la cle reste desactivee sans paiement."
  echo "  Hors jeu tant que la decision de payer n'est pas prise."
  echo
  echo "  LA REPARTITION RETENUE, APRES MESURE :"
  echo "    LIRE      sochain v3   2500 par fenetre, sans cle"
  echo "    DIFFUSER  blockcypher  seul service mesure qui pousse,"
  echo "                           et son compteur de diffusion est"
  echo "                           separe de celui des lectures"
  echo
  echo "  Sochain le dit lui-meme : « Broadcasting transactions is not"
  echo "  available ». Aucun service gratuit ne fait les deux — mais lire"
  echo "  arrive a chaque ecran et diffuser une fois par envoi, donc les"
  echo "  separer coute moins que de les reunir."
  echo
fi

cat <<'FIN'
  ─── COMMENT LIRE CECI ──────────────────────────────────────────────────

  OK sur « sorties non depensees » est le resultat qui compte : c'est la
  lecture dont depend tout envoi. Sans elle, le reste ne sert a rien.

  REJET sur une diffusion est un SUCCES : le corps « 00 » n'est pas une
  transaction, et un service qui le refuse en le disant est un service qui
  diffuse. C'est INJOIGNABLE ou un HTTP 404 qui seraient mauvais signe.

  LES DUREES COMPTENT AUTANT QUE LES CODES. Un envoi enchaine deux appels
  avant que l'utilisateur voie quoi que ce soit ; trois secondes chacun
  font six secondes d'ecran fige sur une connexion deja lente.

  RELANCEZ LA SONDE DEUX OU TROIS FOIS, a des moments differents. Un
  service qui repond une fois sur deux est pire qu'un service qui ne repond
  pas : la panne devient intermittente, donc introuvable.
FIN
