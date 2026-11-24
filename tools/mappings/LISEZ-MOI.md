# Les `mapping.txt`, version par version

`./gradlew assembleRelease` **réécrit** `app/build/outputs/mapping/release/mapping.txt`
à chaque fois. Celui qui est sur le disque décrit donc la dernière version
construite — pas celle qui tourne chez les utilisateurs.

Un crash remonté par Telegram porte des noms comme `j.T:1288`. Sans le
`mapping.txt` **de cette version-là**, ils ne veulent rien dire, et ne
voudront jamais rien dire : l'information est perdue, pas cachée.

## À faire après chaque build que tu distribues

```bash
V=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1 | cut -d+ -f1)
mkdir -p "tools/mappings/$V"
cp app/build/outputs/mapping/release/mapping.txt "tools/mappings/$V/"
```

## Pour lire une pile

```bash
node tools/retracer.mjs "j.T:1288 ← j.W:9 ← j.R:164"
```

Il reconnaît le numéro de version collé depuis le rapport Telegram et
cherche l'archive correspondante en premier. S'il ne la trouve pas, il le
**dit** avant de retomber sur le build courant — un retraçage faux est pire
qu'un retraçage absent.

## Ces fichiers sont-ils sensibles ?

Un `mapping.txt` révèle les noms d'origine de tes classes. Ce dépôt est
public, et ces noms le sont déjà : le code source est à côté. Rien de
nouveau n'est divulgué.
