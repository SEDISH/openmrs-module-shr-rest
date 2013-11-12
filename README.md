OpenHIE Shared Health Record: REST Interface
============================================

A simple RESTful interface for the SHR

Overview
--------
The simple SHR REST interface module that mimics the API expected by the content handler module in a RESTful way. This allows for an easy way to test the system without building a complex interface module.

Requests
--------
The module supports POST and GET requests on the following endpoints:

POST: ws/rest/v1/shr/document

Parameters:
* patientId - The patient identifer
* patientIdType - The type of identifier
* providerId - The provider identifier
* providerIdType - The type of identifier
* encounterType - A name describing the type of encounter
* formatCode - The format code of the submission
* isURL - (optional) isURL=true indicates that the submitted payload is a URL reference

Submit a document for a particular patient.

GET: ws/rest/v1/shr/documents

Parameters:
* contentType - The type of contents to retrieve
* patientId - The patient identifier
* patientIdType - The type of identifier
* dateStart - (optional) Format yyyy-MM-dd'T'HH:mm:ss
* dateEnd - (optional) Format yyyy-MM-dd'T'HH:mm:ss

Query for patient documents of a particular content type.
Note that the response content type (determined by the Accept header) is not the same as the contentType of the document and response can either be in XML or JSON.

See https://wiki.ohie.org/display/SUB/Simple+SHR+REST+interface+module for request examples.
