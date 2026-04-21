# ⚠️ URGENT: SECURITY BREACH REMEDIATION

## Issue
Google OAuth2 credentials were exposed in the GitHub repository and detected by GitGuardian.

## Immediate Actions Required

### 1. Revoke Compromised Credentials (DO THIS NOW!)

1. Go to [Google Cloud Console - Credentials](https://console.cloud.google.com/apis/credentials)
2. Find the OAuth 2.0 Client ID: `463203865712-n8q2qu0jfh6rvnvfbbn4cqo5ofkid8um`
3. **DELETE** this client ID immediately
4. Create a new OAuth 2.0 Client ID

### 2. Generate New Credentials

1. In Google Cloud Console, click **"+ CREATE CREDENTIALS"**
2. Select **"OAuth client ID"**
3. Choose application type: **"Web application"**
4. Add authorized redirect URIs:
   - Development: `http://localhost:3000/profile/google-callback`
   - Production: `https://your-production-domain.com/profile/google-callback`
5. Copy the new Client ID and Client Secret

### 3. Configure Environment Variables

#### On Windows (Development):
```powershell
# Set environment variables in PowerShell
$env:GOOGLE_CLIENT_ID="your-new-client-id"
$env:GOOGLE_CLIENT_SECRET="your-new-client-secret"
$env:GOOGLE_REDIRECT_URI="http://localhost:3000/profile/google-callback"
$env:JWT_SECRET="your-secure-random-jwt-secret"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="admin"
```

#### On Linux/Mac (Development):
```bash
export GOOGLE_CLIENT_ID="your-new-client-id"
export GOOGLE_CLIENT_SECRET="your-new-client-secret"
export GOOGLE_REDIRECT_URI="http://localhost:3000/profile/google-callback"
export JWT_SECRET="your-secure-random-jwt-secret"
export DB_USERNAME="root"
export DB_PASSWORD="admin"
```

#### For Production:
Set these variables in your hosting platform:
- **Google Cloud Run**: Use Secret Manager
- **AWS**: Use Systems Manager Parameter Store or Secrets Manager
- **Azure**: Use Key Vault
- **Heroku**: Use Config Vars in dashboard

### 4. Remove Secrets from Git History

The compromised credentials are still in your Git history. You need to remove them:

#### Option A: Using git filter-repo (Recommended)
```bash
# Install git-filter-repo
pip install git-filter-repo

# Remove the file from history
git filter-repo --path backend/src/main/resources/application.properties --invert-paths

# Force push to GitHub
git push origin --force --all
```

#### Option B: Using BFG Repo-Cleaner
```bash
# Download BFG from https://rtyley.github.io/bfg-repo-cleaner/
java -jar bfg.jar --delete-files application.properties

# Clean up and push
git reflog expire --expire=now --all
git gc --prune=now --aggressive
git push origin --force --all
```

⚠️ **Warning**: Force pushing rewrites history. Coordinate with all team members!

### 5. Update Your Local Configuration

1. Copy the template file:
   ```bash
   cd backend/src/main/resources
   cp application.properties.template application.properties
   ```

2. Edit `application.properties` with your new credentials (this file is now gitignored)

3. Or better yet, just set the environment variables and use the current version

### 6. Verify .gitignore

The `.gitignore` has been updated to prevent future commits of sensitive files:
```
**/src/main/resources/application.properties
**/src/main/resources/application-*.properties
.env
.env.local
```

### 7. Additional Security Measures

1. **Generate a new JWT secret**: Use a cryptographically secure random string
   ```bash
   # Generate a secure JWT secret (256 bits)
   openssl rand -base64 32
   ```

2. **Review database credentials**: Change your MySQL password if it's exposed

3. **Enable 2FA**: Enable two-factor authentication on your GitHub account

4. **Set up branch protection**: Require code review before merging to main

5. **Use secret scanning**: Enable GitHub secret scanning in repository settings

## What's Been Fixed

✅ Created `application.properties.template` with placeholder values
✅ Updated `application.properties` to use environment variables
✅ Added sensitive configuration files to `.gitignore`
✅ Documented security remediation steps

## Testing After Fix

1. Set environment variables
2. Start the backend: `./mvnw spring-boot:run`
3. Verify OAuth flow still works
4. Check logs for any configuration errors

## Prevention

- **Never commit secrets** to version control
- **Always use environment variables** for sensitive data
- **Use secret management tools** in production
- **Review code** before committing
- **Enable GitHub secret scanning**
- **Use pre-commit hooks** to scan for secrets (e.g., `git-secrets`, `detect-secrets`)

## Need Help?

If you encounter issues:
1. Check that environment variables are set correctly
2. Verify Google OAuth settings in Cloud Console
3. Check application logs for configuration errors
4. Ensure redirect URIs match exactly

---
*Generated on: March 2, 2026*
*Severity: CRITICAL*
*Status: Action Required*
