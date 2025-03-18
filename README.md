# maine-forms-workflow-app

This repository contains the code for the "Forms and Workflow" mobile application, also known as "
RouteManifest".

## Setup

After cloning this repo, the following steps must be performed in order to successfully build the
project in Android Studio:

1. Log in to the Trimble Artifactory at https://artifactory.trimble.tools/ to create your user
    - If you are not currently logged into Trimble SSO, you will be prompted to set up Multi Factor
      Authentication on your first login.
        - Use Google Authenticator or Authy on your smartphone to set it up by scanning the provided
          QR Code.
        - Learn more about Trimble MFA
          at https://support.trimble.cloud/support/solutions/articles/2500000352

2. Set up an API Key
    - Go to https://artifactory.trimble.tools/artifactory/webapp/#/profile
    - Click on the gear icon to generate an API token
    - Click on the copy icon to copy it to your clipboard

3. Create or modify the file ~/.gradle/gradle.properties (Windows:
   USER_HOME/.gradle/gradle.properties), and add the following lines:
    ```
    artifactory_user=<YOUR_EMAIL>@trimble.com
    artifactory_password=<YOUR_API_KEY>
    ```
4. Set up your Android Studio's "build variant" to `stgDebug` for the `:app` module (for further
   details on build flavors continue reading this file)

## Collaboration Guidelines

The complete documentation and flows followed by this repository can be found at
the ["Manual Testing Process"](https://docs.google.com/document/d/1Hl4K90x2orpXup4y7IXQxpE5QfPpM_aS792agb3oe_w)
document.

### Branching Strategy

There are two types of branches: _long lived_ and _short lived_ ones - the former being branches
that **are never deleted** and the later being branches **that are deleted once merged into another
branch**.

| Name        | Type        | Description                                                                                                                                                                                              | Merges into ... |
| ----------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------- |
| `master`    | Long-Lived  | This is usually where our production code is at                                                                                                                                                          | N.A.            |
| `develop`   | Long-Lived  | This is usually where our *
latest* code is at, until it is promoted to production                                                                                                                        | N.A.            |
| `release/*` | Short-Lived | This is a subset of code/features from `develop` that will be promoted to production (`master`), code living here will go thru a regression test before merging                                          | `master`        |
| `feature/*` | Short-Lived | This is a branch created from `develop` that contains work-in-progress by an engineer, next step on a feature is to have it code-reviewed and ready for testing                                          | `test/*`        |
| `test/*`    | Short-Lived | This is a branch created from a `develop` that contains a feature that **

has been code-reviewed** but stills needs to have its quality assured by another QA or Software
Engineer before becoming stable | `develop`       | | `bugfix/*`  | Short-Lived | This is a branch
created from a *
long-lived* branch whenever a bugs needs to be fixed, next-step is to have this bug code-reviewed
and ready for testing | `test/*`        |

#### Developing new features or fixes

The usual flow for an engineer is:

1. Pick up an JIRA issue to work on, for instance *MAPP-123321*
2. Create a new branch `feature/MAPP-123321` and work on that branch - this branch should be
   created **from the `develop` branch**
3. Create a new Pull Request once the code is done, locally tested and meeting the acceptance
   criteria. This PullRequest should target the `test/MAPP-123321` branch
4. Merge the code into `test/MAPP-123321` once the code is reviewed by peer QA and Software
   Engineers
5. Create a new PullRequest from `test/MAPP-123321` into `develop`, ping QA Engineers and related
   folks for tests to be done on its artifacts
6. Merge the code into `develop`

#### Releasing a version

The usual flow for a release is:

1. Create a new branch for testing and temporary bugfixing, for instance `release/1.2.3.321`, this
   branch should be created **from the `master` branch**
2. Merge the `develop` branch into the new `release/1.2.3.321` branch to promote changes into
   production
3. Ensure that all teams have their Test Cases ready and have reserved capacity throughout their
   sprints to do **regression testing**
4. Run all applicable **regression test cases** on the artifacts of `release/1.2.3.321`
5. Merge the code into `master`
6. Collaborate with the Product team to ensure the resulting `apk` is uploaded to Play Store.


## App Check Information

This project uses Google's Firebase App Check feature, which adds an extra layer to the project
regarding the protection of the backend resources, you could check more information on
the [firebase documentation.](https://firebase.google.com/docs/app-check)

For debug builds, there is a specific process that must be followed in order to access Firebase services.

1) Start debugging the application
2) Suddenly close the application by pressing back button or close it from the recent apps section before authentication is completed
3) In Logcat, you should see a log statement that looks like something along the lines of
```
D/com.google.firebase.appcheck.debug.internal.DebugAppCheckProvider: Enter this debug secret into the allow list in the Firebase Console for your project: <debug AppCheck token>
```

4) Copy the token and migrate over to the [AppCheck settings in Firebase Console](https://console.firebase.google.com/u/0/project/forms-workflow-stg-55a487ab/appcheck/apps)
5) In the row for `RouteManifest NextGen` app, click the menu icon on the right side and select "Manage Debug Tokens"
6) Click "Add debug token" and create a new entry for your token.
7) Add a name for the new token. Make sure to give it an easily identifiable name, as you may need to delete and create a new one in the future.
8) Paste the token from the Logcat log statement and click Save
9) Force stop the application

*Note: 
If you skip Steps 2 and 9 then you won't get HPN for both New trips and New messages
So If you follow the steps as it is then you won't face this issue. 

If you ever uninstall then re-install this application or clear the storage, a new token will be generated and you'll need to repeat this process, but make sure to delete your previous token*

### App Check Debug Troubleshooting

While running locally the app, we had stumbled into some errors related to **permission denied**
while querying Firebase:

```
sten for Query(target=Query(EDVIR/5097/11058130/Enabled order by __name__);limitType=LIMIT_TO_FIRST) failed: Status{code=PERMISSION_DENIED, description=Missing or insufficient permissions., cause=null}
```

Investigating we had discovered that this is related to the usage of local keys, and because of that
the App Check package key validation does not work and the above error occurs.

In order to solve the key related issue, please refer
to [this documentation](https://firebase.google.com/docs/app-check/android/debug-provider).

### Getting SHAH 256 Fingerprint Key

#### Using Android Studio

To get your SHAH 256 Fingerprint key to register it on firebase and got the AppCheck configured we
need to do some steps. With that in mind let's start the work around this scenario.

First, you will need to perform a few settings on your Android Studio.

Go to:

- **File >> Settings >> Experimental**

Once you are on this screen you will need to disable the "**Do not build Gradle task list during
Gradle Sync**"

[![](This is an example of how the screen should look like)](https://drive.google.com/file/d/1ZiOinbqPPDJ6DN8P-JYhAR4EHdRMTtob/view?usp=sharing)

Moving forward you will need to navigate to the Gradle panel of your Android Studio, once you
navigate to this panel you will need to:

- Right-click in the parent folder >> Select the option **Reload Gradle Project**

[![](This is an example of how the screen should look like)](https://drive.google.com/file/d/1bzM0RT4Aw44DOSNoYkaEqCf46qkiKb4w/view?usp=sharing)

Once this process is done, you will have a folder called **Tasks** created.

Click on that folder and navigate to the folder called **Android**. Once you're in this folder
double click on the task called **signingReport**.

Wait until the task runs and you will have the SHAH 256 in the output of the Build tab.

#### Using Command Line

Navigate to the root folder of the project and run on your terminal the following command:

```bash
.\gradlew signingReport
```

## Feature Flagging with Remote Config

We can enable/disable or tune features by using a concept know as "Feature Flagging". Feature Flags
are means that we can use to remotely change how our applications behaves, without needing to deploy
a new version or asking users to enable/disable something on their settings. In practical terms this
means that we can two (or more) versions simultaneously and toggle between each version on the fly,
allowing us to effectively "test" in production without causing outages because we can roll things
back just as fast as we can deploy them. Check
this [Article by Martin Fowler](https://martinfowler.com/articles/feature-toggles.html) for more
information on the concept itself.

This project leverages [Firebase RemoteConfig](https://firebase.google.com/docs/remote-config) to do
so.

| Environment | Link | | - | - | | Staging
| https://console.firebase.google.com/project/forms-workflow-stg-55a487ab/config | | Prod
| https://console.firebase.google.com/project/forms-workflow-prod-7f1153bb/config |

The interface `FeatureGatekeeper` and its implementation `RemoteConfigGatekeeper` handle the
abstractions and decoupling between Driver Workflow and Firebase Remote Config. If you'd like to add
a new feature flag consider following this workflow:

1. Create a new entry on `FeatureGatekeeper.KnownFeatureFlags`
2. Go to Firebase's RemoteConfig (links above) and create a new feature flag and its value!

If you're adding a type that isn't yet supported:

1. Create a new function on the `FeatureGatekeeper` interface
2. Implement it on `RemoteConfigGatekeeper` by using Remote Config's SDK
3. Add new Unit Tests to ensure it behaves as expected for default and non-default cases

## Build variants and logs in Datadog

we have 2 build types (`DEBUG and RELEASE`) and 2 environments (`STAGE and PROD`). If we want to see
logs in `Datadog` for a selected environment we need to choose the right build variant. In total we
have 4 build variants.

1. **stgDebug** don't emit logs. It is in `stg` environment
2. **prodDebug** don't emit logs. It is in `prod` environment
3. **stgRelease** emit logs. It is in `stg` environment
4. **prodRelease** emit logs. It is in `prod` environment

## Espresso Ui Test

We have an util to send dispatch automatically from the test. If we want to use it please follow the mentioned steps

1. SendDispatchUtilTest this is the test file which contains the test which will send dispatch via test
2. For the dispatch to be send please download the route-manifest-1.1-SNAPSHOT.jar file and place somewhere in your local machine
3. Change the directoryPath with the path from your local machine
4. It is suggested to keep the dispatch file also in the same directory
5. Mention the jarFile name
6. Mention the appropriate dispatch file name
7. Mention the needed number of dispatches
8. Run the appropriate test then the dispatch will be sent

In Ui test we have added wait in the execution of each test it's because the view is taking some time to render. 