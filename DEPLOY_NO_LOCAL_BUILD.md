# Deploy without building on your laptop

You only need **Git** on your machine. **Maven, Java, and SAM are not required locally** — GitHub Actions builds and deploys for you.

## What happens when you push

```
git push  →  GitHub Actions  →  mvn package  →  sam deploy  →  AWS Lambda + API
```

---

## One-time setup (about 30 minutes)

### Step 1 — Put code on GitHub

1. Create a repo at https://github.com/new (e.g. `fresh`)
2. On your laptop (only git needed):

```bash
cd /Users/sexydevil/src/automation_src/fresh/water_meter_service
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git branch -M main
git push -u origin main
```

---

### Step 2 — Create AWS account & find Account ID

1. Sign in to https://console.aws.amazon.com
2. Click your name (top right) → the **12-digit Account ID** is shown — copy it.

---

### Step 3 — GitHub OIDC provider in AWS (once per AWS account)

1. AWS Console → **IAM** → **Identity providers** → **Add provider**
2. Type: **OpenID Connect**
3. Provider URL: `https://token.actions.githubusercontent.com`
4. Audience: `sts.amazonaws.com`
5. **Add provider**

(Skip if this provider already exists.)

---

### Step 4 — IAM role for GitHub Actions

1. IAM → **Roles** → **Create role**
2. Trusted entity: **Web identity**
3. Identity provider: `token.actions.githubusercontent.com`
4. Audience: `sts.amazonaws.com`
5. **Next**
6. Attach policy: **AdministratorAccess** (simplest for learning; tighten later)
7. Role name: `github-actions-water-meter-deploy`
8. **Create role**

---

### Step 5 — Lock role to your GitHub repo only

1. Open role **github-actions-water-meter-deploy**
2. **Trust relationships** → **Edit trust policy**
3. Replace the JSON with (edit `YOUR_ACCOUNT_ID`, `YOUR_USERNAME`, `YOUR_REPO`):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::YOUR_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:YOUR_USERNAME/YOUR_REPO:*"
        }
      }
    }
  ]
}
```

4. **Update policy**

---

### Step 6 — Edit the workflow file

Open `.github/workflows/deploy-water-meter-service.yml` in **this repo** (same folder as `pom.xml`):

`/Users/sexydevil/src/automation_src/fresh/water_meter_service/.github/workflows/deploy-water-meter-service.yml`

```yaml
role-to-assume: arn:aws:iam::YOUR_ACCOUNT_ID:role/github-actions-water-meter-deploy
```

with your real Account ID.

Optional: change `AWS_REGION` (default `ap-south-1`) if you use another region.

Commit and push:

```bash
git add .github/workflows/deploy-water-meter-service.yml
git commit -m "Configure AWS deploy role"
git push
```

---

### Step 7 — Run the deploy

**Automatic:** push any change under `v_switch_app/water_meter_service/` to `main`.

**Manual:** GitHub → your repo → **Actions** → **Deploy Water Meter Service** → **Run workflow**.

Wait 5–10 minutes (first run downloads Maven dependencies).

Green check = deployed.

---

### Step 8 — Get your API URL

**GitHub:** Actions → latest run → **Print API URL** step at the bottom.

**AWS Console:** **CloudFormation** → stack `water-meter-service` → **Outputs** → `ApiUrl`

Test in a browser or:

```bash
curl "https://XXXX.execute-api.ap-south-1.amazonaws.com/Prod/testlamda"
```

Expected: `Hello world`

---

## Day to day (after setup)

```bash
# edit code in v_switch_app/water_meter_service/
git add .
git commit -m "Describe your change"
git push
```

GitHub rebuilds and redeploys automatically.

---

## Troubleshooting

| Error | Fix |
|-------|-----|
| `Could not assume role with OIDC` | Trust policy repo name must match exactly: `YOUR_USERNAME/YOUR_REPO` |
| `Access Denied` during deploy | Role needs IAM/CloudFormation/Lambda/S3/API Gateway permissions |
| Workflow not starting | Push to `main` and change files under `water_meter_service/` |
| Maven/build fails | Open failed log on GitHub Actions; fix Java code, push again |
| 502 on API URL | AWS → Lambda → function → **Monitor** → **View CloudWatch logs** |

---

## You do NOT need on your laptop

- Maven
- Java
- SAM CLI
- AWS CLI (optional; only for convenience)

## You DO need

- Git
- GitHub account
- AWS account
- One-time IAM setup (Steps 3–5)
- Workflow file with your Account ID (Step 6)
