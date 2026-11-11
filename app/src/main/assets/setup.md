SMS Google Sheet Bridge - setup

1. Open script.google.com and create a new project.
2. Replace Code.gs with the file from apps-script/Code.gs.
3. In Project Settings, show the manifest and paste appsscript.json.
4. Run the setup function once and approve the permissions.
   The execution log shows the shared secret and the spreadsheet URL.
5. Deploy > New deployment > Web app.
   Execute as: Me.
   Who has access: Anyone.
6. Copy the Web app URL. It must end with /exec.
   It looks like https://script.google.com/macros/s/XXXX/exec
7. In SMS Google Sheet Bridge, open Settings, paste the URL and the shared secret.
   The secret is at least 32 characters.
8. Tap Save and test. A green notice means the sheet is reachable.

Notes
- Keep the secret private. Anyone with the URL and secret can write to the sheet.
- Use one spreadsheet per phone. Batch numbers start at 1 on each phone.
- Requests are signed and must arrive within 5 minutes. Turn on automatic time.
- The phone deletes a batch after the sheet confirms it. The sheet is not deleted.
- If a deploy changes the URL, update Settings.
