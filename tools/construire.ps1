<#
═══════════════════════════════════════════════════════════════════════════
CONSTRUIRE VAULTEX ET DÉPOSER L'APK, EN UNE COMMANDE
═══════════════════════════════════════════════════════════════════════════

    powershell -ExecutionPolicy Bypass -File .\tools\construire.ps1

Options :

    -SansPull      ne pas faire `git pull` d'abord
    -Release       construire la version de production au lieu du debug
    -Ouvrir        ouvrir le dossier de dépôt à la fin

─── POURQUOI UN SCRIPT PLUTÔT QU'UNE LIGNE À RECOPIER ───────────────────

Parce que la ligne à recopier a coûté trois allers-retours.

D'abord un filtre `Select-String "^e: |APK depose|BUILD"` qui affichait
« BUILD FAILED » sans jamais montrer POURQUOI : la ligne « ERREUR
values-en/strings.xml:1100 » ne correspondait à aucun motif. On voyait
l'échec sans la cause, deux fois de suite.

Ensuite un chemin tapé à la main sur la ligne de commande, avec une
espace dedans, que PowerShell coupe en deux avant de le passer à
`gradlew.bat`.

Une commande qu'on retape chaque fois finit par être retapée de travers.
Celle-ci est écrite une fois.

─── CE QU'IL NE FAIT PAS ────────────────────────────────────────────────

Il ne règle pas le dossier de dépôt : c'est `apk.sortie` dans
`local.properties`, et `gradlew ouEstLApk` écrit la ligne à y coller. Un
script qui irait modifier ce fichier toucherait à côté de mots de passe
de signature, et ce n'est pas sa place.

Il ne touche pas non plus à git au-delà d'un `pull` — pas de commit, pas
de push, pas de changement de branche.
═══════════════════════════════════════════════════════════════════════════
#>

param(
    [switch]$SansPull,
    [switch]$Release,
    [switch]$Ouvrir
)

# On se place à la racine du dépôt, quel que soit l'endroit d'où le script
# est lancé : `cd` oublié est la première cause de « ça ne marche pas ».
$racine = Split-Path -Parent $PSScriptRoot
Set-Location $racine

$variante = if ($Release) { 'Release' } else { 'Debug' }
Write-Host ""
Write-Host "VaultEx — construction $variante" -ForegroundColor Cyan
Write-Host "Depot : $racine" -ForegroundColor DarkGray
Write-Host ""

# ─── Mise à jour ────────────────────────────────────────────────────

if (-not $SansPull) {
    Write-Host "git pull..." -ForegroundColor DarkGray
    git pull
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "git pull a echoue. Rien n'a ete construit." -ForegroundColor Red
        Write-Host "Regle le conflit, ou relance avec -SansPull pour construire tel quel."
        exit 1
    }
}

# ─── Où l'APK doit atterrir ─────────────────────────────────────────

<#
ON LE DEMANDE AVANT DE CONSTRUIRE, PAS APRÈS.

Un chemin faux découvert à la fin d'une compilation, c'est trois minutes
perdues pour une faute de frappe. `ouEstLApk` répond en quelques
secondes, et sans réglage il écrit la ligne à coller — avec le Bureau
réel de cette machine, OneDrive compris.
#>
Write-Host ""
.\gradlew.bat ouEstLApk --console=plain 2>&1 |
    Select-String "vaultex :" | ForEach-Object { $_.Line.Trim() }

# ─── Construction ───────────────────────────────────────────────────

<#
LE FILTRE LAISSE PASSER CE QUI SERT À COMPRENDRE.

`e:` sont les erreurs du compilateur Kotlin, `ERREUR` celles du
vérificateur de chaînes, `FAILURE`/`What went wrong`/`Caused by` celles
de Gradle lui-même. Le filtre précédent n'avait que `^e:` et `BUILD` :
il montrait l'échec en cachant sa cause.
#>
Write-Host ""
Write-Host "Compilation en cours (quelques minutes la premiere fois)..." -ForegroundColor DarkGray
Write-Host ""

$motifs = "^e: |ERREUR|AVERTISSEMENT|FAILURE|What went wrong|Caused by|^\* |APK depose|BUILD "

.\gradlew.bat "assemble$variante" --console=plain 2>&1 |
    Select-String $motifs | ForEach-Object { $_.Line.TrimEnd() }

$ok = $LASTEXITCODE -eq 0

# ─── Verdict ────────────────────────────────────────────────────────

Write-Host ""
if (-not $ok) {
    Write-Host "ECHEC. Les lignes ci-dessus disent pourquoi." -ForegroundColor Red
    Write-Host ""
    Write-Host "Si elles ne suffisent pas, relance SANS filtre :" -ForegroundColor DarkGray
    Write-Host "    .\gradlew.bat assemble$variante --console=plain" -ForegroundColor DarkGray
    exit 1
}

<#
ON RELIT LE DOSSIER PLUTÔT QUE DE CROIRE LA LIGNE DE LOG.

« APK depose dans … » est écrit par Gradle juste après la copie. Le
fichier peut malgré tout ne pas être là — dossier synchronisé, antivirus,
disque plein. Regarder vaut mieux que supposer, et l'heure affichée dit
tout de suite si c'est bien la construction de maintenant.
#>
#
# ON RELIT local.properties PLUTÔT QUE DE RAPPELER GRADLE.
#
# Un second `gradlew ouEstLApk` redémarrerait une JVM pour apprendre une
# chose que le fichier porte déjà. La ligne est lue ici comme elle l'est
# dans build.gradle.kts : clé exacte, valeur telle qu'écrite — ni
# `Properties` ni interprétation des contre-obliques.
#
$depot = $null
if (Test-Path 'local.properties') {
    $depot = Get-Content 'local.properties' |
        Where-Object { $_ -match '^\s*apk\.sortie\s*=' } |
        ForEach-Object { ($_ -split '=', 2)[1].Trim() } |
        Select-Object -First 1
}

if ($depot -and (Test-Path $depot)) {
    $nom = "VaultEx-$($variante.ToLower()).apk"
    $fichier = Join-Path $depot $nom
    if (Test-Path $fichier) {
        $f = Get-Item $fichier
        Write-Host "OK — $nom" -ForegroundColor Green
        Write-Host "   $($f.FullName)"
        Write-Host "   $([math]::Round($f.Length / 1MB, 1)) Mo, ecrit a $($f.LastWriteTime.ToString('HH:mm:ss'))"
        if ($Ouvrir) { explorer.exe $depot }
    } else {
        Write-Host "La compilation a reussi, mais $nom n'est PAS dans $depot" -ForegroundColor Yellow
        Write-Host "Essaie :  .\gradlew.bat deposerApk"
    }
} else {
    Write-Host "Compilation reussie." -ForegroundColor Green
    Write-Host "Aucun dossier de depot regle — l'APK est reste dans app\build\outputs\apk\." -ForegroundColor Yellow
    Write-Host "Pour en regler un :  .\gradlew.bat ouEstLApk"
}
Write-Host ""
