# Logos des sources d'actualités

Cinq fichiers de 96×96, **8 Ko au total**, un par source du fil
d'actualités. Ils s'affichent dans le rond à gauche de chaque titre.

## Déposer sur le relais

```powershell
cd tools\relais-cours

foreach ($n in "cryptoast","journalducoin-2","cointribune","rfi","france24") {
  npx.cmd wrangler kv key put --config wrangler.toml --remote `
    --binding MEMOIRE "img:logo-$n.webp" --path "..\annonces\logos\logo-$n.webp"
}
```

Le `-2` de Journal du Coin n'est pas une faute de frappe : son premier
logo, le bandeau complet, était illisible à 38 points. Le nom a changé
parce que l'ancien est déjà en cache sur les téléphones qui ont ouvert
l'écran.

Vérifier les tailles servies, **avant** de conclure quoi que ce soit à
l'écran :

```powershell
cd ..\..
$attendu = @{ cryptoast=1908; "journalducoin-2"=718; cointribune=1956; rfi=884; france24=2318 }
foreach ($n in $attendu.Keys) {
  $t = curl.exe -s -o NUL -w "%{size_download}" "https://<relais>/img/logo-$n.webp"
  Write-Host "$n -> $t (attendu $($attendu[$n]))"
}
```

Un chiffre qui ne correspond pas veut dire qu'un autre fichier a été
envoyé. C'est arrivé avec les portraits de l'équipe, et ça a coûté une
soirée : on regardait l'écran et le relais, jamais l'octet.

## Pourquoi ces images-là sont acceptables

Il y en a **cinq en tout, une par source** — pas une par article. Une
illustration d'article aurait coûté un à deux mégaoctets à chaque
ouverture d'écran, sur le forfait mobile de quelqu'un à Ouagadougou.
Cinq logos de deux kilo-octets, c'est huit kilo-octets téléchargés une
fois et gardés en cache pour des mois.

C'est la seule raison pour laquelle le fil porte des images alors que
l'écran les refusait : le nombre, pas le goût.

## ATTENTION — LE NOM NE SE RÉUTILISE JAMAIS

Les téléphones gardent ces images par ADRESSE. Déposer un logo différent
sous un nom déjà servi laisse l'ancien s'afficher pendant des mois, sans
qu'aucun écran ne le signale.

Un logo qui change prend un nom neuf — `logo-rfi-2.webp` — et
`SOURCES_ACTUS` dans `worker.js` suit.

## Ajouter une source

1. L'ajouter à `SOURCES_ACTUS` avec son champ `logo`
2. Préparer l'image : carrée, 96 px, fond transparent ou celui du logo

```bash
convert source.png -background none -resize 96x96 -gravity center \
  -extent 96x96 -strip -quality 85 -define webp:method=6 logo-nom.webp
```

3. Déposer, puis déployer

Le champ `logo` entre dans l'empreinte qui sert de clé de cache : changer
un logo invalide donc la liste d'articles toute seule, sans purge.

**Une source sans logo marche.** Le rond reste teinté et vide, à la même
place — la ligne garde sa forme. C'est ce qui permet d'ajouter une source
avant d'avoir son image.
