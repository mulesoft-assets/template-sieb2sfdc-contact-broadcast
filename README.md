
# Anypoint Template: Siebel to Salesforce Contact Broadcast

# License Agreement
This template is subject to the conditions of the 
<a href="https://s3.amazonaws.com/templates-examples/AnypointTemplateLicense.pdf">MuleSoft License Agreement</a>.
Review the terms of the license before downloading and using this template. You can use this template for free 
with Mule Enterprise Edition, CloudHub, or as a trial in Anypoint Studio.

# Use Case
As a Salesforce admin I want to synchronize contacts from Siebel to Salesforce.

This template serves as a foundation for the process of broadcasting accounts from Siebel to Salesforce instance. Every time there is a new contact or a change in an already existing one, the integration polls for changes in the Siebel source instance and updates the contact in the Salesforce.

Requirements have been set not only to be used as examples, but also to establish a starting point to adapt your integration to your requirements.

As implemented, this Anypoint Template leverages the Mule batch module. The batch job is divided into  *Process* and *On Complete* stages. 
The integration is triggered by a scheduler that queries the newest Siebel updates and creations matching a filter criteria, and executes the batch job. During the *Process* stage, each Siebel contact is filtered depending on if the contact has an  matching contact in Salesforce. 
The last step of the *Process* stage groups the accounts and creates or updates the accounts in Salesforce. Finally during the *On Complete* stage the template logs statistics data into the console.

# Considerations

To make this Anypoint Template run, there are certain preconditions that must be considered. All of them deal with the preparations in both source and destination systems, that must be made in order for all to run smoothly. Failing to do so could lead to unexpected behavior of the template.

## Salesforce Considerations

Here's what you need to know about Salesforce to get this template to work.

### FAQ

- Where can I check that the field configuration for my Salesforce instance is the right one? See: <a href="https://help.salesforce.com/HTViewHelpDoc?id=checking_field_accessibility_for_a_particular_field.htm&language=en_US">Salesforce: Checking Field Accessibility for a Particular Field</a>
- Can I modify the Field Access Settings? How? See: <a href="https://help.salesforce.com/HTViewHelpDoc?id=modifying_field_access_settings.htm&language=en_US">Salesforce: Modifying Field Access Settings</a>


### As a Data Destination

There are no considerations with using Salesforce as a data destination.

## Siebel Considerations

Here's what you need to know to get this template to work with Siebel.

This template may use date time or timestamp fields from Siebel to do comparisons and take further actions.
While the template handles the time zone by sending all such fields in a neutral time zone, it cannot discover the time zone in which the Siebel instance is on.
It is up to you to provide such information. See [Oracle's Setting Time Zone Preferences](http://docs.oracle.com/cd/B40099_02/books/Fundamentals/Fund_settingoptions3.html).

### As a Data Source

To make the Siebel connector work smoothly, you have to provide the correct version of the Siebel jars (Siebel.jar, SiebelJI_enu.jar) that work with your Siebel installation.

# Run it!
Simple steps to run this template.


## Running On Premises
In this section we help you run your template on your computer.


### Where to Download Anypoint Studio and the Mule Runtime
If you are a newcomer to Mule, here is where to get the tools.

+ [Download Anypoint Studio](https://www.mulesoft.com/platform/studio)
+ [Download Mule runtime](https://www.mulesoft.com/lp/dl/mule-esb-enterprise)


### Importing a Template into Studio
In Studio, click the Exchange X icon in the upper left of the taskbar, log in with your
Anypoint Platform credentials, search for the template, and click **Open**.


### Running on Studio
After you import your template into Anypoint Studio, follow these steps to run it:

+ Locate the properties file `mule.dev.properties`, in src/main/resources.
+ Complete all the properties required as per the examples in the "Properties to Configure" section.
+ Right click the template project folder.
+ Hover your mouse over `Run as`.
+ Click `Mule Application (configure)`.
+ Inside the dialog, select Environment and set the variable `mule.env` to the value `dev`.
+ Click `Run`.


### Running on Mule Standalone
Complete all properties in one of the property files, for example in mule.prod.properties and run your app with the corresponding environment variable. To follow the example, this is `mule.env=prod`. 


## Running on CloudHub
While creating your application on CloudHub (or you can do it later as a next step), go to Runtime Manager > Manage Application > Properties to set the environment variables listed in "Properties to Configure" as well as the **mule.env**.


### Deploying your Anypoint Template on CloudHub
Studio provides an easy way to deploy your template directly to CloudHub, for the specific steps to do so check this


## Properties to Configure
To use this template, configure properties (credentials, configurations, etc.) in the properties file or in CloudHub from Runtime Manager > Manage Application > Properties. The sections that follow list example values.
### Application Configuration
**Common Configuration**
		
+ page.size `100`
+ scheduler.frequency `30000`
+ scheduler.start.delay `0`		

**Salesforce Connector Configuration**

+ sfdc.username `bob.dylan@sfdc`
+ sfdc.password `DylanPassword123`
+ sfdc.securityToken `avsfwCUl7apQs56Xq2AKi3X`
	
**Oracle Siebel Connector Configuration**

+ sieb.user `user`
+ sieb.password `secret`
+ sieb.server `server`
+ sieb.serverName `serverName`
+ sieb.objectManager `objectManager`
+ sieb.port `2321`

**Watermarking default last query timestamp in MM/dd/yy HH:mm:ss**

+ watermark.default.expression `"05/19/2015 10:00:00"`

# API Calls
Salesforce imposes limits on the number of API calls that can be made. Therefore calculating this amount may be an important factor to consider. This template calls to the API can be calculated using the formula:

*** 2 * X + X / 200***

***X*** is the number of Contacts to be synchronized on each run. 

Divide by ***200*** because, by default, Contacts are gathered in groups of 200 for each Upsert API Call in the commit step. Also consider that calls are executed repeatedly every polling cycle.	

For instance if 10 records are fetched from origin instance, then 21 API calls are made (20 + 1).
Note that for each contact for which there is not an existing matching account, there is another API call to Salesforce.


# Customize It!
This brief guide intends to give a high level idea of how this template is built and how you can change it according to your needs.
As Mule applications are based on XML files, this page describes the XML files used with this template.

More files are available such as test classes and Mule application files, but to keep it simple, we focus on these XML files:

* config.xml
* businessLogic.xml
* endpoints.xml
* errorHandling.xml

## config.xml
Configuration for connectors and configuration properties are set in this file. Change the configuration here. All parameters that can be modified are in a properties file, which is the recommended place to make your changes. However if you want to do core changes to the logic, you need to modify this file.

In the Studio visual editor, the properties are on the *Global Element* tab.

## businessLogic.xml
Functional aspects of this template are implemented in this file, directed by the batch job that handles the creates or updates. The following message processors constitute four high level actions that fully implement the logic of this template:

1. Job execution is invoked from triggerFlow (endpoints.xml) every time there is a new query executed asking for created or updated Contacts.
2. During the *Process* stage, each Siebel contact is filtered depending on if it has an existing matching contact in  Salesforce.
3. The last step of the *Process* stage groups the contacts and creates or updates them in Salesforce.
Finally during the *On Complete* stage, the template logs output statistics data to the console.

## endpoints.xml
This file provides a flow containing the Scheduler that periodically queries Siebel for updated or created Contacts that meet the defined criteria in the query. The template then executes the batch job process with the query results based on this file.

## errorHandling.xml
This is the right place to handle how your integration reacts depending on the different exceptions. 
This file provides error handling that is referenced by the main flow in the business logic.
