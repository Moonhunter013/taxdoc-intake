# Creates the Week 2 issues on GitHub and adds them to the "TaxDoc Intake" project board.
# Prerequisites (one time): winget install -e --id GitHub.cli ; gh auth login ; gh auth refresh -s project
# Run from the repo root:
#   powershell -ExecutionPolicy Bypass -File .\docs\planning\week-2\create-issues.ps1

$ErrorActionPreference = "Stop"
$repo    = "Moonhunter013/taxdoc-intake"
$project = "TaxDoc Intake"
$dir     = "docs/planning/week-2"

gh label create "week-2"             --repo $repo --color "8250df" --description "Week 2 work" --force
gh label create "extraction-service" --repo $repo --color "0e8a16" --description "Python / FastAPI service" --force
gh label create "planning-service"   --repo $repo --color "d93f0b" --description "Java / Spring Boot service" --force
gh label create "frontend"           --repo $repo --color "1d76db" --description "Angular UI" --force
gh label create "docs"               --repo $repo --color "c5def5" --description "ADRs and documentation" --force

$issues = @(
    @{ file = "01-clients-and-document-listing.md";  title = "Clients and per-client document listing";        labels = "week-2,extraction-service" },
    @{ file = "02-planning-persistence.md";          title = "Planning persistence: own schema, JPA, Flyway";  labels = "week-2,planning-service" },
    @{ file = "03-tax-year-config.md";               title = "Tax-year rules config with verified 2026 values"; labels = "week-2,planning-service" },
    @{ file = "04-extraction-client-and-tax-profile.md"; title = "Extraction API client and TaxProfile";       labels = "week-2,planning-service" },
    @{ file = "05-strategy-engine-scorp.md";         title = "Strategy engine + S-corp election";              labels = "week-2,planning-service" },
    @{ file = "06-retirement-and-hsa-strategies.md"; title = "Retirement, HSA and excess SS strategies";       labels = "week-2,planning-service" },
    @{ file = "07-plans-api.md";                     title = "Plans API";                                      labels = "week-2,planning-service" },
    @{ file = "08-ui-upload.md";                     title = "UI: upload documents";                           labels = "week-2,frontend" },
    @{ file = "09-ui-review-and-correct.md";         title = "UI: review and correct extracted fields";        labels = "week-2,frontend" },
    @{ file = "10-ui-plan-results.md";               title = "UI: plan results";                               labels = "week-2,frontend" }
)

foreach ($i in $issues) {
    Write-Host "Creating: $($i.title)"
    gh issue create --repo $repo --title $i.title --body-file "$dir/$($i.file)" --label $i.labels --project $project
}
Write-Host "Done."
