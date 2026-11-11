# Photos de l'équipe — avatars prêts à déposer

Trois fichiers de 256×256, détourés, transparents, sans métadonnées, de
7 à 9 Ko chacun. Ils sont le résultat de `../avatar-equipe.sh`, dont
l'en-tête explique chaque étape et pourquoi elle existe.

## Déposer sur le relais

Depuis la racine du dépôt, sous PowerShell :

```powershell
cd tools\relais-cours

foreach ($n in "moumine","rasmane","sakande") {
  npx.cmd wrangler kv key put --config wrangler.toml --remote `
    --binding MEMOIRE "img:$n-2.webp" --path "..\annonces\equipe\$n.webp"
}
```

Puis vérifier les tailles servies **avant** de toucher à `CHANGE_EQUIPE` :

```powershell
cd ..\..
foreach ($n in "moumine-2","rasmane-2","sakande-2") {
  $t = curl.exe -s -o NUL -w "%{size_download}" "https://<relais>/img/$n.webp"
  Write-Host "$n -> $t octets"
}
```

Attendu : **9016**, **8074**, **6948**. Un autre chiffre veut dire qu'un
autre fichier a été envoyé — ça s'est produit, et c'est précisément
pourquoi ce dossier existe.

## Pourquoi ces fichiers sont dans le dépôt

Ils y sont pour une raison bête et suffisante : le transfert par la
conversation n'aboutissait pas sur la machine de déploiement, et des
photos d'origine se retrouvaient déposées à la place des avatars
préparés — Windows renomme au lieu de remplacer, et `--path` envoyait
sans broncher un fichier de 1,7 Mo.

Un fichier versionné ne peut pas se tromper de version.

## ATTENTION — LE NOM NE SE RÉUTILISE JAMAIS

Les téléphones gardent ces images en cache très longtemps, par ADRESSE.
Déposer une image différente sous un nom déjà servi laisse l'ancienne
s'afficher pendant des mois, sans qu'aucun écran ne le signale.

C'est arrivé ici : les premiers avatars ont été déposés sous
`moumine.webp`, `rasmane.webp`, `sakande.webp`, d'où le suffixe `-2` des
commandes ci-dessus. Une photo qui change prend un nom neuf, et
`CHANGE_EQUIPE` suit.

## Remplacer une photo, ou en ajouter une

```bash
./tools/annonces/avatar-equipe.sh photo-source.png prenom
```

Le script mesure la tête, recadre, harmonise l'exposition, retire les
métadonnées et écrit un aperçu rond à regarder avant de déposer. Pour une
photo qui n'est pas détourée, son en-tête donne la marche à suivre.
