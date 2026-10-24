# AWS setup (Week 0)

Do these steps once. Budget alarm first. Everything here is in **us-east-1**.

## 1. Account safety (AWS Console, ~10 min)
1. Create the AWS account (or use an existing one) and **turn on MFA for the root user**.
2. Check **Billing → Free tier / Credits** to see what credits your account has.
3. Stop using the root user after this section.

## 2. Budget alarm (do this before anything else)
Console: **Billing → Budgets → Create budget → Monthly cost budget**, $25, alerts to your email
at 50% actual, 100% actual and 100% forecasted.

Or with the AWS CLI (replace `YOUR_EMAIL_HERE` in `budget-notifications.json` first):

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws budgets create-budget \
  --account-id "$ACCOUNT_ID" \
  --budget file://budget.json \
  --notifications-with-subscribers file://budget-notifications.json
```

## 3. A dev identity with only what Week 1 needs
Create an IAM user (or IAM Identity Center user) called `taxdoc-dev`. Attach an inline policy
from `dev-user-policy.json`, after replacing `BUCKET_NAME_HERE` with your bucket name from step 4.
Then on your machine:

```bash
aws configure --profile taxdoc     # region: us-east-1, output: json
aws sts get-caller-identity --profile taxdoc
```

Never commit access keys. `.env` is git-ignored; the app reads `AWS_PROFILE=taxdoc`.

## 4. Upload bucket
Bucket names are global, so add something unique (e.g. your initials):

```bash
BUCKET=taxdoc-intake-dev-uploads-ph01
aws s3api create-bucket --bucket "$BUCKET" --region us-east-1 --profile taxdoc-admin
aws s3api put-public-access-block --bucket "$BUCKET" --profile taxdoc-admin \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-lifecycle-configuration --bucket "$BUCKET" --profile taxdoc-admin \
  --lifecycle-configuration '{"Rules":[{"ID":"expire-dev-uploads","Status":"Enabled","Filter":{"Prefix":""},"Expiration":{"Days":30}}]}'
```

(`taxdoc-admin` = whatever admin profile you use to create resources; the dev user can't create buckets.)
Put the bucket name in `.env` as `EXTRACTION_S3_BUCKET`.

## 5. Smoke-test Textract (uses 1 page of free tier)
```bash
aws s3 cp ../../samples/forms/client-01_w2.pdf "s3://$BUCKET/smoke/client-01_w2.pdf" --profile taxdoc
aws textract analyze-document --profile taxdoc \
  --document "{\"S3Object\":{\"Bucket\":\"$BUCKET\",\"Name\":\"smoke/client-01_w2.pdf\"}}" \
  --feature-types FORMS > ../../samples/textract-fixtures/client-01_w2.json
```
That JSON becomes your first test fixture in Week 1, so CI never needs to call AWS.

## Cost traps to avoid later
- **NAT Gateway**: ~$33/month just to exist. Avoid it in Week 4.
- Forgetting to tear down ECS/RDS/ALB after recording the demo.
