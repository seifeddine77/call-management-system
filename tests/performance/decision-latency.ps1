<#
.SYNOPSIS
    Mesure reproductible du temps de décision par voie : Cache (Redis) / Moteur de règles / IA (Gemma).

.DESCRIPTION
    Envoie des requêtes contrôlées sur POST /api/decisions/analyze, lit les champs
    `source` (RULES/AI) et `cached` retournés par le service (preuve d'attribution),
    puis calcule min/médiane/moyenne/max/p95 par voie.

    NON DESTRUCTIF : chaque appel crée un enregistrement de décision en base, mais le
    script SUPPRIME automatiquement tous les enregistrements de test à la fin
    (via DELETE /api/decisions/ticket/{id}).

.EXAMPLE
    pwsh ./tests/performance/decision-latency.ps1
    pwsh ./tests/performance/decision-latency.ps1 -BaseUrl http://localhost:8085 -AiRuns 5 -RulesRuns 8 -CacheRuns 8
#>
param(
    [string]$BaseUrl = "http://localhost:8085",
    [int]$AiRuns = 5,
    [int]$RulesRuns = 8,
    [int]$CacheRuns = 8
)

$ErrorActionPreference = "Stop"
$analyze = "$BaseUrl/api/decisions/analyze"
$testIds = [System.Collections.ArrayList]@()
$rows = [System.Collections.ArrayList]@()

function Invoke-Decision($body) {
    $tid = [guid]::NewGuid().ToString()
    [void]$script:testIds.Add($tid)
    $body.ticketId = $tid
    $json = $body | ConvertTo-Json -Compress
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-RestMethod -Uri $script:analyze -Method POST -ContentType "application/json" -Body $json -TimeoutSec 120
        $sw.Stop()
        [void]$script:rows.Add([PSCustomObject]@{
            Voie     = $r.source
            Cached   = $r.cached
            Decision = $r.decision
            Temps_ms = [math]::Round($sw.Elapsed.TotalMilliseconds)
        })
    } catch {
        $sw.Stop()
        Write-Warning "Echec d'un appel: $($_.Exception.Message)"
    }
}

function Get-Stats($vals) {
    $vals = @($vals | Where-Object { $null -ne $_ })
    if ($vals.Count -eq 0) { return $null }
    $s = $vals | Sort-Object
    $n = $s.Count
    $p95idx = [Math]::Min($n - 1, [Math]::Ceiling($n * 0.95) - 1)
    [PSCustomObject]@{
        n   = $n
        min = $s[0]
        med = $s[[Math]::Floor($n / 2)]
        moy = [Math]::Round(($vals | Measure-Object -Average).Average)
        max = $s[$n - 1]
        p95 = $s[$p95idx]
    }
}

# Paramètres métier de base (le champ `source` retourné fait foi, pas l'étiquette)
$casAI    = @{ typePiece = "MOTEUR"; ageVehiculeAns = 2;  kilometrage = 25000;  categorieClient = "PARTICULIER"; typeSinistre = "PANNE_MECANIQUE"; pieceReference = "MOT-12345" }
$casRULES = @{ typePiece = "INCONNU"; ageVehiculeAns = 15; kilometrage = 280000; categorieClient = "ENTREPRISE";  typeSinistre = "LITIGE_COMPLEXE"; transcriptionText = "a evaluer" }

# Graine aléatoire : garantit des paramètres jamais déjà présents dans le cache Redis
# (sinon une voie "fraîche" retomberait sur le cache d'une exécution précédente).
$seed = Get-Random -Minimum 1000 -Maximum 90000

Write-Host "== Mesure du temps de decision (cible: $BaseUrl) ==" -ForegroundColor Cyan

Write-Host "1/3 Voie IA ($AiRuns appels frais, ~10s chacun)..." -ForegroundColor Yellow
1..$AiRuns | ForEach-Object { $c = $casAI.Clone(); $c.kilometrage = 25000 + $seed + $_; Invoke-Decision $c }

Write-Host "2/3 Voie CACHE ($CacheRuns appels, memes parametres)..." -ForegroundColor Yellow
$cacheCase = $casAI.Clone(); $cacheCase.kilometrage = 25000 + $seed  # jeu de parametres dedie au cache
Invoke-Decision ($cacheCase.Clone())  # warm-up (passe par l'IA, alimente le cache)
1..$CacheRuns | ForEach-Object { Invoke-Decision ($cacheCase.Clone()) }

Write-Host "3/3 Voie REGLES ($RulesRuns appels frais)..." -ForegroundColor Yellow
1..$RulesRuns | ForEach-Object { $c = $casRULES.Clone(); $c.kilometrage = 280000 + $seed + $_; Invoke-Decision $c }

# Regroupement par voie réelle (d'après source/cached renvoyés par le service)
$cache = ($rows | Where-Object { $_.Cached }).Temps_ms
$rules = ($rows | Where-Object { -not $_.Cached -and $_.Voie -eq 'RULES' }).Temps_ms
$ai    = ($rows | Where-Object { -not $_.Cached -and $_.Voie -eq 'AI' }).Temps_ms

Write-Host ""
Write-Host "================ RESULTATS (temps en ms) ================" -ForegroundColor Green
"{0,-16} {1,4} {2,7} {3,7} {4,7} {5,7} {6,7}" -f "Voie", "n", "min", "med", "moy", "max", "p95"
foreach ($g in @(@{Nom="Cache (Redis)"; V=$cache}, @{Nom="Moteur regles"; V=$rules}, @{Nom="IA (Gemma)"; V=$ai})) {
    $st = Get-Stats $g.V
    if ($st) { "{0,-16} {1,4} {2,7} {3,7} {4,7} {5,7} {6,7}" -f $g.Nom, $st.n, $st.min, $st.med, $st.moy, $st.max, $st.p95 }
    else     { "{0,-16} {1,4} (aucune mesure)" -f $g.Nom, 0 }
}

# Nettoyage automatique des enregistrements de test
Write-Host ""
Write-Host "Nettoyage des enregistrements de test..." -ForegroundColor Yellow
$deleted = 0
foreach ($id in $testIds) {
    try { Invoke-RestMethod -Uri "$BaseUrl/api/decisions/ticket/$id" -Method DELETE -TimeoutSec 15 | Out-Null; $deleted++ } catch {}
}
Write-Host "Supprimes : $deleted / $($testIds.Count)" -ForegroundColor Green
