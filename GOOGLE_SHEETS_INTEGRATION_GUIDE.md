# Google Sheets Auto-Export Integration Guide

## Overview
Teachers can now configure their system to automatically export student data to a designated Google Sheet. Whenever students are added or updated, the data automatically syncs to the configured sheet.

## Features
✅ **Automatic Setup** - Permissions granted during Google login  
✅ **Teacher-Only Access** - Only teachers can configure and access this feature  
✅ **Automatic Syncing** - Data syncs when students are uploaded/imported  
✅ **URL Validation** - System validates sheet accessibility before saving  
✅ **Fallback Support** - Local CSV export remains available and unchanged  
✅ **Error Resilience** - Failed syncs don't block student uploads  

## Setup Instructions

### Step 1: Log In with Google
Teachers already have the required permissions from logging in with Google. The system automatically requests Google Sheets access during the login process.

### Step 2: Create or Prepare a Google Sheet
1. Create a new Google Sheet or use an existing one
2. Make a note of the sheet URL: `https://docs.google.com/spreadsheets/d/{SHEET_ID}/edit...`
3. **Share the spreadsheet with your Google account**
   - The sheet must be accessible to the Google account you used to log in

### Step 3: Configure the Google Sheet in Settings
1. Go to **Profile** page
2. Scroll to **Google Sheets Auto-Export** section
3. Click **"Configure Sheet"** button
4. Paste your Google Sheet URL
5. Click **"Save"**
   - System will validate that you have access
   - If validation fails, check that the sheet is properly shared
6. Confirmation message will appear once saved

## How It Works

### Automatic Sync Triggers
Data automatically syncs to your Google Sheet when:
- ✓ New students are uploaded via the import feature
- ✓ Existing students are updated during import

### Data Structure
The sheet will contain:
- **Row 1**: Column headers (CLASS, TEAMCODE, MEMBER#, STUDENTID, LASTNAME, FIRSTNAME, EMAIL, ADVISOREMAIL, [QUESTIONNAIRE_1], [QUESTIONNAIRE_2], etc.)
- **Rows 2+**: Student data with questionnaire scores

All existing data is cleared and replaced with the latest data each sync.

### What Syncs
- All student information (class, team, member number, ID, name, email)
- Assigned adviser email
- Questionnaire scores (averaged from evaluations)

## Managing Your Configuration

### View Current Configuration
1. Go to **Profile** → **Google Sheets Auto-Export**
2. Your configured sheet URL will be displayed
3. Click **"Update Sheet"** to change it

### Update Your Sheet
1. Click **"Update Sheet"**
2. Paste a new Google Sheet URL
3. Click **"Save"** to apply changes
4. The system will validate access before saving

### Requirements for Each New Sheet
- Must be shared with your Google account email (the one you logged in with)
- Ensure you have **Editor** permissions on the sheet

## Troubleshooting

### "Invalid Google Sheets URL or insufficient permissions"
**Problem**: URL is invalid or the sheet isn't shared with your account  
**Solution**:
1. Verify the URL format: `https://docs.google.com/spreadsheets/d/{ID}/edit...`
2. Open the sheet in your browser to confirm access
3. Check sheet sharing settings - it must be shared with your email
4. Try again with a different sheet

### Data not syncing after upload
**Problem**: Student upload succeeded but data didn't appear in sheet  
**Solution**:
1. Confirm sheet URL is still valid and accessible
2. Check server logs for sync errors
3. Try manually uploading students again
4. Note: Failed syncs don't prevent uploads - students are added to system

### Can't upload the Google Sheet URL
**Problem**: System rejects the URL  
**Solution**:
1. Make sure the URL contains the sheet ID: `.../d/{LONG_ID}/edit...`
2. Don't include query parameters (they'll be ignored)
3. Ensure the sheet is shared with your Google account
4. Try a different sheet to test the feature

## Technical Details

### Google API Scope
The system requests the `spreadsheets` scope during login to:
- Read sheet information and structure
- Write data to your sheet
- Maintain your existing sheet structure

### Data Format
- Headers: First row contains column names
- Data: Plain text values
- Numbers: Stored as text for compatibility
- Empty cells: Left blank

### Performance
- Sync typically completes in 2-5 seconds
- Large exports may take longer
- Upload success is confirmed immediately (sync is background)

## Limitations

- Only TEACHER role can configure Google Sheets
- Each teacher can configure one Google Sheet at a time
- Sheet name is always "Sheet1" (first sheet)
- Existing sheet data is replaced during each sync (not appended)

## Privacy & Security

- Your OAuth token is securely stored and only used for accessing your sheets
- Google Sheet URL is stored in the database
- Only you can access and modify your configured sheet
- Data synced is the same data shown in local exports

## Questions or Issues?

Contact your system administrator or check the application logs for detailed error information.

## How It Works

### Automatic Sync Triggers
Data automatically syncs to your Google Sheet when:
- ✓ New students are uploaded via the import feature
- ✓ Existing students are updated during import

### Data Structure
The sheet will contain:
- **Row 1**: Column headers (CLASS, TEAMCODE, MEMBER#, STUDENTID, LASTNAME, FIRSTNAME, EMAIL, ADVISOREMAIL, [QUESTIONNAIRE_1], [QUESTIONNAIRE_2], etc.)
- **Rows 2+**: Student data with questionnaire scores

All existing data is cleared and replaced with the latest data each sync.

### What Syncs
- All student information (class, team, member number, ID, name, email)
- Assigned adviser email
- Questionnaire scores (averaged from evaluations)

## Managing Your Configuration

### View Current Configuration
1. Go to **Profile** → **Google Sheets Export**
2. Your configured sheet URL will be displayed
3. Click **"Update Sheet"** to change it

### Update Your Sheet
1. Click **"Update Sheet"**
2. Paste a new Google Sheet URL
3. Click **"Save"** to apply changes
4. The system will validate access before saving

### Requirements for Each New Sheet
- Must be shared with your linked Google account email
- Ensure you have **Editor** permissions on the sheet

## Troubleshooting

### "Invalid Google Sheets URL or insufficient permissions"
**Problem**: URL is invalid or the sheet isn't shared with your account  
**Solution**:
1. Verify the URL format: `https://docs.google.com/spreadsheets/d/{ID}/edit...`
2. Open the sheet in your browser to confirm access
3. Check sheet sharing settings - it must be shared with your email
4. Try again with a different sheet

### "Google account not linked"
**Problem**: You haven't linked your Google account yet  
**Solution**:
1. Go to Profile
2. Click "Link Google Account" in the Google Account section
3. Complete the OAuth flow
4. Then configure your Google Sheet

### Data not syncing after upload
**Problem**: Student upload succeeded but data didn't appear in sheet  
**Solution**:
1. Confirm sheet URL is still valid and accessible
2. Check server logs for sync errors
3. Try manually uploading students again
4. Note: Failed syncs don't prevent uploads - students are added to system

### Can't upload the Google Sheet URL
**Problem**: System rejects the URL  
**Solution**:
1. Make sure the URL contains the sheet ID: `.../d/{LONG_ID}/edit...`
2. Don't include query parameters (they'll be ignored)
3. Ensure the sheet is shared with your linked Google email
4. Try a different sheet to test the feature

## Technical Details

### Google API Scope
The system requests the `spreadsheets` scope to:
- Read sheet information and structure
- Write data to your sheet
- Maintain your existing sheet structure

### Data Format
- Headers: First row contains column names
- Data: Plain text values
- Numbers: Stored as text for compatibility
- Empty cells: Left blank

### Performance
- Sync typically completes in 2-5 seconds
- Large exports may take longer
- Upload success is confirmed immediately (sync is background)

## Limitations

- Only TEACHER role can configure Google Sheets
- Each teacher can configure one Google Sheet at a time
- Sheet must be accessible via OAuth (not service account)
- Sheet name is always "Sheet1" (first sheet)
- Existing sheet data is replaced during each sync (not appended)

## Privacy & Security

- Your OAuth token is securely stored and only used for accessing your sheets
- Google Sheet URL is stored in the database
- Only you can access and modify your configured sheet
- Data synced is the same data shown in local exports

## Questions or Issues?

Contact your system administrator or check the application logs for detailed error information.
