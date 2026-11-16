# Ce qui est parti, et quand

## Pourquoi ce fichier existe

Le dossier d'à côté explique en cinq cents lignes **quoi** envoyer et
**pourquoi**. Il ne notait nulle part ce qui **avait été** envoyé.

Ça s'est payé : `securite-01-faux-site.txt` — l'alerte sur gbash.tools et
les faux « agents » qui promettent de récupérer les fonds volés chez
Binance — a été écrite le 31 octobre et n'est partie que le 16 novembre.
Seize jours. Pendant ce temps l'arnaque tournait, et rien dans le dépôt
ne permettait de dire si l'avertissement était parti ou non.

Un fichier prêt à envoyer ressemble en tout point à un fichier déjà
envoyé. C'est le seul endroit où la différence s'écrit.

## La règle

Une ligne par envoi **réel** — pas pour les `--moi`, qui ne touchent
qu'un appareil. `send-announcement.sh` affiche la ligne à copier juste
après un envoi réussi, au moment où l'information existe.

Une annonce envoyée deux fois prend deux lignes : savoir qu'un message
est passé deux fois explique des choses qu'on chercherait ailleurs.

## Le journal

| date | fichier | destinataires | note |
|------|---------|---------------|------|
| 2026-11-16 | `securite-01-faux-site.txt` | tous | Alerte gbash.tools. Préparée le 31 octobre, partie avec seize jours de retard. Vérifiée au préalable avec `--moi`. |

## Ce qui reste à envoyer

Ces fichiers existent et ne sont jamais partis. Ils n'ont pas tous
vocation à partir — certains attendent que la fonctionnalité qu'ils
annoncent soit livrée.

| fichier | en attente de quoi |
|---------|--------------------|
| `maj-06-v1068.txt` | **prête** — la version 1.0.1068 est construite et vérifiée ; reste à décider comment les 223 récupèrent l'APK |
| `change-01-bientot.txt` | l'annonce Orange Money — à envoyer quand la décision est prise |
| `evenement-01-siao.txt` | daté : ne vaut que pendant le SIAO |
| `evenement-02-bon-weekend.txt` | un vendredi |

Les cinq `maj-*` antérieures sont **périmées** : elles datent du
25 septembre ou nomment la V1.0.604. Ne pas les renvoyer telles quelles —
une annonce qui parle d'une version dépassée fait douter de toutes les
autres. `maj-06-v1068.txt` les remplace.

Le reste du dossier — `swap-*`, `esprit-*`, `depot-*`, `citation-*`,
`frais-*`, `pi-*` — est du fonds de roulement, à envoyer au rythme qu'on
choisit. Noter ici ceux qui partent, pour ne pas servir deux fois le même
message à quinze jours d'intervalle.
