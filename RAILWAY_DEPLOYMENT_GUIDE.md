# Railway Deployment Guide (Backend + Database + Frontend)

## Files Created for Deployment:
- ✅ `backend/Dockerfile` - Docker configuration for Java backend
- ✅ `backend/src/main/resources/application.properties` - Updated with environment variables
- ✅ `backend/src/main/java/group9/advisor_eval_system/config/CorsConfig.java` - Updated CORS for production

---

## Step 1: Push Code to GitHub

```bash
git add .
git commit -m "Add Railway deployment files"
git push origin main
```

---

## Step 2: Deploy Backend + MySQL Database on Railway

### 2.1: Create Railway Project

1. Go to https://railway.app
2. Sign in with GitHub
3. Click **New Project**
4. Select **Deploy from GitHub repo**
5. Choose your `capstone-merged-evaluation-system` repo
6. Click **Deploy**

Railway will automatically detect the Dockerfile and start building.

### 2.2: Create MySQL Database

While backend is building, add database:

1. In your Railway project, click **+ Create**
2. Select **Database** → **MySQL**
3. Railway automatically creates MySQL service with credentials

### 2.3: Configure Backend Environment Variables

1. Click on your **Web Service** (backend)
2. Go to **Variables** tab
3. Add these variables:

```
SPRING_DATASOURCE_URL=jdbc:mysql://${{MYSQL_HOSTNAME}}:3306/${{MYSQL_DATABASE}}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=${{MYSQL_USER}}
SPRING_DATASOURCE_PASSWORD=${{MYSQL_PASSWORD}}
JWT_SECRET=<paste output from: openssl rand -hex 32>
PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

**To generate JWT_SECRET**, run in terminal:
```bash
openssl rand -hex 32
```

4. Click **Add Variable** for each one
5. Click **Save**

Railway will automatically redeploy with the new variables.

### 2.4: Wait for Deployment

- Backend will build and deploy (5-10 minutes)
- MySQL will be ready within 1-2 minutes
- Check **Deployments** tab for status

**Copy your backend URL** (looks like): `https://capstone-merged-evaluation-system-production.up.railway.app`

---

## Step 3: Deploy Frontend to Vercel

### 3.1: Create Vercel Project

1. Go to https://vercel.com/dashboard
2. Click **Add New** → **Project**
3. Import your GitHub repo
4. Click **Select Framework Preset**
5. Choose **Create React App**

### 3.2: Configure Frontend

Set these in the project settings:

- **Root Directory:** `frontend`
- **Build Command:** `npm run build`
- **Output Directory:** `build`

### 3.3: Add Environment Variables

In Vercel, go to **Settings** → **Environment Variables**

Add:
```
REACT_APP_API_BASE_URL=https://capstone-merged-evaluation-system-production.up.railway.app/api
```

**Replace the URL** with your actual Railway backend URL!

### 3.4: Deploy

Click **Deploy** and wait for it to finish (2-5 minutes).

**Copy your frontend URL** (looks like): `https://capstone-merged-evaluation-system.vercel.app`

---

## Step 4: Update Backend CORS (if needed)

If your Vercel URL is different, update `CorsConfig.java`:

In `backend/src/main/java/group9/advisor_eval_system/config/CorsConfig.java`, the CORS patterns should already include:
- `https://*.vercel.app` (covers all Vercel URLs)
- `https://*.railway.app` (covers all Railway URLs)

If you need a specific domain, add it explicitly.

---

## Step 5: Test the Deployment

1. Visit your Vercel frontend URL
2. Try logging in or making an API call
3. Open browser console (F12) to check for errors
4. Check Railway logs if there are issues

---

## Troubleshooting

### Build fails on Railway
- Check **Deployments** → **View logs**
- Make sure `backend/Dockerfile` exists
- Ensure `Root Directory` is set to `backend` (if it's a monorepo)

### Database connection errors
- Verify environment variables are set correctly
- Check MySQL service is running in Railway
- Ensure `SPRING_DATASOURCE_URL` format is correct

### CORS errors on frontend
- Verify `REACT_APP_API_BASE_URL` matches your Railway backend URL
- Make sure URL includes `/api` path
- Check that backend CORS config includes Vercel domain

### Frontend can't reach backend
- Test backend URL directly in browser (should return JSON error, not "Connection refused")
- Verify environment variable in Vercel is set correctly
- Redeploy Vercel frontend after changing the URL

---

## Important Notes

- **Railway URL** from backend: `https://capstone-merged-evaluation-system-production.up.railway.app`
- **Vercel URL** from frontend: `https://capstone-merged-evaluation-system.vercel.app`
- **MySQL** runs internally on Railway and connects automatically
- **Free tier** on Railway lasts 5 days, then you need to add payment method

---

## Quick Reference

| Component | Service | URL Format |
|-----------|---------|-----------|
| Backend API | Railway | `https://capstone-merged-evaluation-system-production.up.railway.app` |
| Database | Railway MySQL | `${{MYSQL_HOSTNAME}}:3306` (internal) |
| Frontend | Vercel | `https://capstone-merged-evaluation-system.vercel.app` |

