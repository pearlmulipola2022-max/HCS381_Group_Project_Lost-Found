# Campus Lost & Found — HCS 381 Java Swing Project

## Overview
A Java Swing desktop application for campus students to report lost or found items, search reports, and update an item's status when it is returned.

## Features
- Dashboard with counts for total reports, lost items, found items, and returned items
- Report form with required-field validation and date validation (`YYYY-MM-DD`)
- Item browser with keyword search and status filter
- Mark an item as returned
- Delete a report after confirmation
- Local persistence in `campus_lost_found.csv` (tab-separated text data)
- Dark navy and orange theme (colours defined at the top of `LostAndFoundApp.java`); uses the Raleway font if it is installed

## Requirements
- JDK 8 or newer (JDK 17 recommended)
- Eclipse IDE

## Run in Eclipse
1. Extract the ZIP file.
2. In Eclipse, choose **File → New → Java Project**.
3. Name the project `HCS381_Campus_Lost_and_Found`.
4. Copy `src/LostAndFoundApp.java` into the project's `src` folder.
5. Open the file and select **Run → Run As → Java Application**.
6. The file `campus_lost_found.csv` is created in the application's working directory when the first report is saved.

## Run from a terminal
From the project folder:
```bash
javac -encoding UTF-8 -d out src/LostAndFoundApp.java
java -cp out LostAndFoundApp
```

## Main screens
- **Dashboard** — overview and quick access to reporting
- **Browse items** — search, filter, mark returned, or delete a report
- **Report an item** — submit lost/found details

## Important limitations
This is a course-project prototype. It uses local storage on one computer, not a shared online database. Contact details are visible in the item table, so use test data for demonstrations and do not store sensitive personal information. Before handing over an item, verify ownership in person. A production version could add student login, role-based access, photo uploads, a central database, and private claim requests.

## Suggested demonstration
1. Open Dashboard and explain the summary cards.
2. Report a lost phone using sample information.
3. Report a found item.
4. Open Browse items, search for `phone`, and filter by status.
5. Select a report and mark it as returned.
6. Close and reopen the app to show that reports are saved locally.
