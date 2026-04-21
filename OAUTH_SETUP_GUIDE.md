# Google OAuth 2.0 Setup Guide - Step by Step

This guide will walk you through deleting compromised credentials and setting up new ones securely.

---

## Part 1: Delete the Compromised OAuth Credentials

### Step 1: Access Google Cloud Console

1. Open your browser and go to: **https://console.cloud.google.com**
2. Sign in with your Google account (the one that owns this project)

### Step 2: Select Your Project

1. At the top of the page, click the **project dropdown** (next to "Google Cloud")
2. Find and select your project (likely named something related to "adviser-evaluation" or similar)
3. If you don't see it, click **"ALL"** tab to see all projects

### Step 3: Navigate to Credentials

1. Click the **☰ hamburger menu** (top-left corner)
2. Hover over **"APIs & Services"**
3. Click **"Credentials"**
   - Direct link: https://console.cloud.google.com/apis/credentials

### Step 4: Identify the Compromised Credential

Look for the OAuth 2.0 Client ID that matches:
- **Client ID**: `463203865712-n8q2qu0jfh6rvnvfbbn4cqo5ofkid8um.apps.googleusercontent.com`

You'll see it listed under **"OAuth 2.0 Client IDs"** section.

### Step 5: Delete the Compromised Credential

1. Find the compromised OAuth client in the list
2. Click the **trash/delete icon** (🗑️) on the right side of that row
3. A confirmation dialog will appear
4. Click **"DELETE"** to confirm
5. ✅ The compromised credential is now revoked!

⚠️ **Note**: Your application will stop working until you complete Part 2.

---

## Part 2: Create New OAuth 2.0 Credentials

### Step 1: Configure OAuth Consent Screen (if not already done)

1. In the **"APIs & Services" > "Credentials"** page
2. If you see a warning about consent screen, click **"CONFIGURE CONSENT SCREEN"**
3. Choose **"External"** (unless you have Google Workspace)
4. Click **"CREATE"**

5. Fill in the required fields:
   - **App name**: `Adviser Evaluation System`
   - **User support email**: Your email
   - **Developer contact email**: Your email
6. Click **"SAVE AND CONTINUE"**

7. On the **"Scopes"** page:
   - Click **"ADD OR REMOVE SCOPES"**
   - Select these scopes:
     - `.../auth/userinfo.email`
     - `.../auth/userinfo.profile`
     - `openid`
   - Click **"UPDATE"**
   - Click **"SAVE AND CONTINUE"**

8. On **"Test users"** page (if in testing mode):
   - Add your email and any team members' emails
   - Click **"SAVE AND CONTINUE"**

9. Review and click **"BACK TO DASHBOARD"**

### Step 2: Create New OAuth Client ID

1. Go back to **"Credentials"** tab
2. Click **"+ CREATE CREDENTIALS"** at the top
3. Select **"OAuth client ID"**

### Step 3: Configure the OAuth Client

1. **Application type**: Select **"Web application"**

2. **Name**: Enter a descriptive name
   - Example: `Adviser Evaluation System - Web Client`

3. **Authorized JavaScript origins**: Click **"+ ADD URI"**
   - For development: `http://localhost:3000`
   - For production: `https://yourdomain.com` (add this later)

4. **Authorized redirect URIs**: Click **"+ ADD URI"**
   - For development: `http://localhost:3000/profile/google-callback`
   - For production: `https://yourdomain.com/profile/google-callback` (add this later)

5. Click **"CREATE"**

### Step 4: Save Your Credentials Securely

A popup will appear with your new credentials:

```
Your Client ID
463203865712-XXXXXXXXXXXXXXXXXXXXXXXX.apps.googleusercontent.com

Your Client Secret
GOCSPX-XXXXXXXXXXXXXXXXXXXXX
```

⚠️ **CRITICAL: DO NOT CLOSE THIS WINDOW YET!**

---

## Part 3: Configure Your Application Securely

### Step 1: Set Environment Variables (NEVER commit these!)

#### On Windows (PowerShell):

Open PowerShell and run:

```powershell
# Set for current session
$env:GOOGLE_CLIENT_ID="YOUR_NEW_CLIENT_ID_HERE"
$env:GOOGLE_CLIENT_SECRET="YOUR_NEW_CLIENT_SECRET_HERE"
$env:GOOGLE_REDIRECT_URI="http://localhost:3000/profile/google-callback"

# Generate a secure JWT secret
$env:JWT_SECRET="YOUR_SECURE_RANDOM_STRING_HERE"

# Database credentials
$env:DB_USERNAME="root"
$env:DB_PASSWORD="admin"
```

**To make these permanent (recommended):**

```powershell
# Set permanently for your user account
[System.Environment]::SetEnvironmentVariable('GOOGLE_CLIENT_ID', 'YOUR_NEW_CLIENT_ID_HERE', 'User')
[System.Environment]::SetEnvironmentVariable('GOOGLE_CLIENT_SECRET', 'YOUR_NEW_CLIENT_SECRET_HERE', 'User')
[System.Environment]::SetEnvironmentVariable('GOOGLE_REDIRECT_URI', 'http://localhost:3000/profile/google-callback', 'User')
[System.Environment]::SetEnvironmentVariable('JWT_SECRET', 'YOUR_SECURE_RANDOM_STRING_HERE', 'User')
[System.Environment]::SetEnvironmentVariable('DB_USERNAME', 'root', 'User')
[System.Environment]::SetEnvironmentVariable('DB_PASSWORD', 'admin', 'User')
```

After setting permanently, **restart your terminal and VS Code**.

#### Generate a Secure JWT Secret:

```powershell
# Run this to generate a secure random string
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
```

Copy the output and use it as your JWT_SECRET.

### Step 2: Verify Environment Variables Are Set

```powershell
# Check if they're set
echo $env:GOOGLE_CLIENT_ID
echo $env:GOOGLE_CLIENT_SECRET
```

You should see your values (not empty).

### Step 3: Verify Your application.properties

Open: `backend/src/main/resources/application.properties`

Make sure it looks like this (NO hardcoded secrets):

```properties
# GOOGLE OAUTH CONFIGURATION
google.client.id=${GOOGLE_CLIENT_ID}
google.client.secret=${GOOGLE_CLIENT_SECRET}
google.redirect.uri=${GOOGLE_REDIRECT_URI:http://localhost:3000/profile/google-callback}
```

✅ This is correct - it reads from environment variables!

### Step 4: Verify .gitignore

Open: `.gitignore` at the root

Make sure these lines are present:

```
### Sensitive Configuration ###
**/src/main/resources/application.properties
**/src/main/resources/application-*.properties
.env
.env.local
```

✅ This prevents accidentally committing secrets!

---

## Part 4: Test Your Setup

### Step 1: Start the Backend

```powershell
cd backend
./mvnw spring-boot:run
```

Watch the console output. Look for:
- ✅ No errors about missing `GOOGLE_CLIENT_ID`
- ✅ Application starts successfully on port 8080

### Step 2: Start the Frontend

In a new terminal:

```powershell
cd frontend
npm start
```

### Step 3: Test OAuth Flow

1. Open browser to: `http://localhost:3000`
2. Log in to your application
3. Go to your profile page
4. Click **"Link Google Account"** button
5. You should be redirected to Google's OAuth consent screen
6. Grant permissions
7. You should be redirected back to your app successfully

### Step 4: Verify It Works

- ✅ No errors in browser console
- ✅ No errors in backend logs
- ✅ Google account successfully linked
- ✅ Can access Google-dependent features

---

## Part 5: Secure Your Git Repository

### Step 1: Check What Git Will Commit

```powershell
git status
```

**Make sure you DO NOT see:**
- ❌ `backend/src/main/resources/application.properties` with actual secrets

**You SHOULD see:**
- ✅ `backend/src/main/resources/application.properties` (if modified with env vars)
- ✅ `backend/src/main/resources/application.properties.template`
- ✅ `.gitignore`
- ✅ `SECURITY_BREACH_REMEDIATION.md`
- ✅ `OAUTH_SETUP_GUIDE.md`

### Step 2: Verify File Contents Before Committing

```powershell
# Check the file being committed
cat backend/src/main/resources/application.properties
```

**Look for**: `${GOOGLE_CLIENT_ID}` and `${GOOGLE_CLIENT_SECRET}` ✅

**NOT**: Actual credential strings ❌

### Step 3: Stage Safe Changes

```powershell
git add backend/src/main/resources/application.properties.template
git add .gitignore
git add SECURITY_BREACH_REMEDIATION.md
git add OAUTH_SETUP_GUIDE.md
git add backend/src/main/resources/application.properties
```

### Step 4: Double-Check What's Being Committed

```powershell
git diff --cached backend/src/main/resources/application.properties
```

**Verify**: You should only see environment variable placeholders like `${GOOGLE_CLIENT_ID}`, NOT actual secrets!

### Step 5: Commit the Security Fixes

```powershell
git commit -m "security: Remove hardcoded OAuth credentials and use environment variables"
```

### Step 6: Remove Secrets from Git History

⚠️ **IMPORTANT**: The old secrets are still in your Git history!

#### Option A: Using git filter-repo (Recommended)

```powershell
# Install git-filter-repo
pip install git-filter-repo

# Backup your repo first!
cd ..
cp -r capstone-adviser-evaluation-system capstone-adviser-evaluation-system-backup

cd capstone-adviser-evaluation-system

# Remove the sensitive file from ALL history
git filter-repo --invert-paths --path backend/src/main/resources/application.properties

# This will remove the file from history, then you need to:
# 1. Copy your current application.properties (with env vars) back
# 2. Re-commit it

# Or you can use this approach to replace content:
git filter-repo --replace-text <(echo "google.client.id=463203865712-n8q2qu0jfh6rvnvfbbn4cqo5ofkid8um.apps.googleusercontent.com==>google.client.id=\${GOOGLE_CLIENT_ID}")
git filter-repo --replace-text <(echo "google.client.secret=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==>google.client.secret=\${GOOGLE_CLIENT_SECRET}")
```

#### Option B: Using BFG Repo-Cleaner

```powershell
# Download BFG from https://rtyley.github.io/bfg-repo-cleaner/
# Then run:

java -jar bfg.jar --replace-text replacements.txt

# Create replacements.txt with:
# 463203865712-n8q2qu0jfh6rvnvfbbn4cqo5ofkid8um.apps.googleusercontent.com==>${GOOGLE_CLIENT_ID}
# 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==>${GOOGLE_CLIENT_SECRET}
```

### Step 7: Force Push (After Coordinating with Team!)

⚠️ **WARNING**: This rewrites history. Tell your team first!

```powershell
git push origin --force --all
```

---

## Part 6: Best Practices to Prevent Future Leaks

### 1. Use a Pre-Commit Hook

Install `git-secrets`:

```powershell
# Install git-secrets
git clone https://github.com/awslabs/git-secrets.git
cd git-secrets
./install.ps1

# Configure for your repo
cd path/to/your/repo
git secrets --install
git secrets --register-aws  # Detects AWS keys
git secrets --add 'GOCSPX-[A-Za-z0-9_-]{28}'  # Google OAuth pattern
git secrets --add '[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com'  # Google Client ID
```

### 2. Enable GitHub Secret Scanning

1. Go to your GitHub repository
2. Click **"Settings"** tab
3. Click **"Code security and analysis"** (left sidebar)
4. Enable:
   - ✅ **Dependency graph**
   - ✅ **Dependabot alerts**
   - ✅ **Dependabot security updates**
   - ✅ **Secret scanning** (if available)

### 3. Review Before Every Commit

**Always run before committing:**

```powershell
# Check what's being committed
git diff

# Check for common secret patterns
git diff | Select-String -Pattern "GOCSPX|\.apps\.googleusercontent\.com|secret|password|api.?key"
```

### 4. Use Environment Variables Checklist

Before committing any config file, verify:

- [ ] No hardcoded passwords
- [ ] No API keys or secrets
- [ ] Uses `${VARIABLE_NAME}` or `${VARIABLE_NAME:default}` syntax
- [ ] Actual secrets are in environment variables only
- [ ] `.gitignore` includes the file (if needed)

### 5. Team Education

Share these rules with your team:

1. **NEVER** commit files with real credentials
2. **ALWAYS** use environment variables for secrets
3. **CHECK** git diff before every commit
4. **VERIFY** .gitignore is working
5. **REPORT** any suspected leaks immediately

### 6. Production Best Practices

When deploying to production:

**Don't use environment variables directly!** Use a secret manager:

- **Google Cloud**: [Secret Manager](https://cloud.google.com/secret-manager)
- **AWS**: [Secrets Manager](https://aws.amazon.com/secrets-manager/) or [Systems Manager Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html)
- **Azure**: [Key Vault](https://azure.microsoft.com/en-us/services/key-vault/)
- **Heroku**: [Config Vars](https://devcenter.heroku.com/articles/config-vars)

---

## Part 7: Quick Reference

### Setting Environment Variables

**Current session only:**
```powershell
$env:VARIABLE_NAME="value"
```

**Permanent (User level):**
```powershell
[System.Environment]::SetEnvironmentVariable('VARIABLE_NAME', 'value', 'User')
```

**Permanent (System level - requires admin):**
```powershell
[System.Environment]::SetEnvironmentVariable('VARIABLE_NAME', 'value', 'Machine')
```

### Checking Environment Variables

```powershell
# Check one variable
echo $env:GOOGLE_CLIENT_ID

# List all environment variables
Get-ChildItem Env:

# Check specific variables
Get-ChildItem Env: | Where-Object { $_.Name -like "*GOOGLE*" }
```

### Removing Environment Variables

```powershell
# Current session
Remove-Item Env:VARIABLE_NAME

# Permanent
[System.Environment]::SetEnvironmentVariable('VARIABLE_NAME', $null, 'User')
```

---

## Troubleshooting

### Issue: "Could not resolve placeholder 'GOOGLE_CLIENT_ID'"

**Solution**: Environment variables not set.

```powershell
# Check if set
echo $env:GOOGLE_CLIENT_ID

# If empty, set them
$env:GOOGLE_CLIENT_ID="your-client-id"
$env:GOOGLE_CLIENT_SECRET="your-client-secret"

# Then restart your application
```

### Issue: OAuth redirect not working

**Problem**: Redirect URI mismatch.

**Solution**: 
1. Check Google Cloud Console → Credentials → Your OAuth Client
2. Verify **Authorized redirect URIs** includes: `http://localhost:3000/profile/google-callback`
3. Make sure there are no typos or trailing slashes

### Issue: "Application.properties not found" after git filter-repo

**Solution**: The file was removed from history. Create it again:

```powershell
cd backend/src/main/resources
cp application.properties.template application.properties
# Then set environment variables as shown above
```

### Issue: VS Code not picking up environment variables

**Solution**: Restart VS Code completely after setting system environment variables.

```powershell
# Close VS Code, then:
# Set variables, then reopen
code .
```

---

## Summary Checklist

Use this checklist to verify everything is secure:

- [ ] Old OAuth credentials deleted from Google Cloud Console
- [ ] New OAuth credentials created
- [ ] Environment variables set (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET)
- [ ] application.properties uses `${VARIABLE_NAME}` syntax
- [ ] .gitignore includes sensitive files
- [ ] Git history cleaned (old secrets removed)
- [ ] Backend starts without errors
- [ ] OAuth login flow works
- [ ] No secrets visible in `git diff`
- [ ] Changes committed and pushed
- [ ] Pre-commit hooks installed (optional but recommended)
- [ ] Team members notified about environment variable requirements

---

## Need Help?

If you get stuck:

1. **Check logs**: Look for specific error messages
2. **Verify environment**: Run `echo $env:GOOGLE_CLIENT_ID`
3. **Review this guide**: Follow each step carefully
4. **Check Google Cloud Console**: Verify OAuth client configuration
5. **Test locally first**: Make sure it works before deploying

---

**Remember**: Secrets should NEVER be in your code. Always use environment variables or secret managers! 🔒

*Last updated: March 2, 2026*
