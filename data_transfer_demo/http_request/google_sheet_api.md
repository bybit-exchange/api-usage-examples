## Using Google Sheets API

### Steps

### Step 1 Create a Project in Google Cloud Platform
   - Go to the Google Cloud Console. [https://console.cloud.google.com/welcome]
   - Click the project drop-down and select New Project.
   - Enter a name for the project, and optionally, edit the provided project ID. Click Create.


### Step 2 Enable the Google Sheets API
   - In the Cloud Console, navigate to the menu item API & Services > Library.
   - Search for "Google Sheets API" and select the matching result.
   - Click Enable.

### Step 3 Create Credentials
   - Navigate to API & Services > Credentials.
   - Click Create Credentials and select OAuth 2.0 Client ID.
   - Choose Desktop App as the application type.
   - Click Create. You will see a dialog with your client ID and client secret.
   - Click OK to close the dialog.
   - Click on the download icon (down arrow) on the right of the credentials you just created to download the credentials.json file.


### Step 4 Integrate with Your Java Application
   - Move the credentials.json to the root directory of your Java project or to a location you can easily reference.
   - Use the Google Sheets API client libraries to authenticate and interact with the API using the provided credentials.json. 

**Notes** 
   Always keep your credentials.json private. Never expose it in public repositories or public locations.
   Follow best practices for storing and handling API keys and credentials.