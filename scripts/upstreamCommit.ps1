param (
    [Parameter(Mandatory=$true)]
    [string]$PurpurHash
)

function Get-Commits($repo, $oldHash) {
    $url = "https://api.github.com/repos/$repo/compare/$($oldHash)...HEAD"
    
    try {
        $response = Invoke-RestMethod -Uri $url -Headers @{"Accept"="application/vnd.github.v3+json"}
        
        $commitLines = $response.commits | ForEach-Object {
            $sha = $_.sha.Substring(0, 8)
            $msg = ($_.commit.message -split "(\r?\n)")[0]
            $msg = $msg -replace '\[ci( |-)skip\]', '[ci/skip]'
            
            "$repo@$sha $msg"
        }
        return $commitLines
    } catch {
        Write-Error "Error: $_"
        exit 1
    }
}

$purpur = Get-Commits "PurpurMC/Purpur" $PurpurHash
$updated = "Purpur"
$disclaimer = "Upstream has released updates that appear to apply and compile correctly"

$logsuffix = "`n`nPurpur Changes:`n" + ($purpur -join "`n")
$log = "Updated Upstream ($updated)`n`n$disclaimer$logsuffix"
$log | git commit -F -

if ($LASTEXITCODE -ne 0) {
    Write-Error "Git commit failed"
    exit 1
}