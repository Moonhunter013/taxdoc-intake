# Creates the Week 1 issues on GitHub and adds them to the "TaxDoc Intake" project board.
# Prerequisites (one time):
#   winget install -e --id GitHub.cli
#   gh auth login
#   gh auth refresh -s project      # lets gh add issues to your project board
# Run from the repo root:
#   .\docs\planning\week-1\create-issues.ps1

$ErrorActionPreference = "Stop"
$repo    = "Moonhunter013/taxdoc-intake"
$project = "TaxDoc Intake"
$dir     = "docs/planning/week-1"

# Labels (--force updates them if they already exist)
gh label create "week-1"             --repo $repo --color "1f6feb" --description "Week 1 work" --force
gh label create "extraction-service" --repo $repo --color "0e8a16" --description "Python / FastAPI service" --force
gh label create "docs"               --repo $repo --color "c5def5" --description "ADRs and documentation" --force
gh label create "testing"            --repo $repo --color "fbca04" --description "Tests and test data" --force

$issues = @(
    @{ file = "01-capture-textract-fixtures.md"; title = "Capture Textract fixtures for all sample forms"; labels = "week-1,extraction-service,testing" },
    @{ file = "02-domain-models.md";             title = "Pydantic models for W-2 and 1099-NEC";            labels = "week-1,extraction-service" },
    @{ file = "03-textract-parser-w2.md";        title = "Textract response parser + W-2 mapper";           labels = "week-1,extraction-service" },
    @{ file = "04-textract-mapper-1099nec.md";   title = "1099-NEC mapper (Rev. Dec 2026 layout)";          labels = "week-1,extraction-service" },
    @{ file = "05-accuracy-tests.md";            title = "Extraction accuracy tests against ground truth";  labels = "week-1,extraction-service,testing" },
    @{ file = "06-confidence-and-validation.md"; title = "Confidence thresholds + W-2 consistency checks";   labels = "week-1,extraction-service" },
    @{ file = "07-upload-endpoint.md";           title = "POST /api/v1/documents upload endpoint";          labels = "week-1,extraction-service" },
    @{ file = "08-mysql-persistence.md";         title = "MySQL persistence with SQLAlchemy + Alembic";     labels = "week-1,extraction-service" },
    @{ file = "09-adrs-week-1.md";               title = "ADRs 0003-0005: services, confidence, sensitive data"; labels = "week-1,docs" }
)

foreach ($i in $issues) {
    Write-Host "Creating: $($i.title)"
    gh issue create --repo $repo --title $i.title --body-file "$dir/$($i.file)" --label $i.labels --project $project
}
Write-Host "Done. Open the board and drag them into order."
