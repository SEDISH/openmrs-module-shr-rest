/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.shr.rest.web.controller;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.*;
import org.openmrs.module.shr.contenthandler.api.Content.Representation;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value="/rest/" + RestConstants.VERSION_1 + "/shr")
public class EncounterController extends BaseRestController {
	
	private static final String ENCOUNTERROLE_UUID_GLOBAL_PROP = "shr.contenthandler.encounterrole.uuid";
	
	Log log = LogFactory.getLog(this.getClass());
	
	
	@RequestMapping(value = "/document", method = RequestMethod.POST)
	@ResponseBody
	public Object postDocument(
			@RequestHeader(value = "Content-Type", required = true) String contentType,
			@RequestParam(value = "patientId", required = true) String patientId,
			@RequestParam(value = "patientIdType", required = true) String patientIdType,
			@RequestParam(value = "providerId", required = true) String providerId,
			@RequestParam(value = "providerIdType", required = true) String providerIdType,
			@RequestParam(value = "encounterType", required = true) String encounterType,
			@RequestParam(value = "typeCodeCode", required = true) String typeCodeCode,
			@RequestParam(value = "typeCodeCodingScheme", required = true) String typeCodeCodingScheme,
			@RequestParam(value = "typeCodeCodeName", required = false) String typeCodeCodeName,
			@RequestParam(value = "formatCodeCode", required = true) String formatCodeCode,
			@RequestParam(value = "formatCodeCodingScheme", required = true) String formatCodeCodingScheme,
			@RequestParam(value = "formatCodeCodeName", required = false) String formatCodeCodeName,
			@RequestParam(value = "isURL", required = false) String isURL,
			@RequestParam(value = "uniqueID", required = false) String uniqueID,
			HttpServletRequest request, HttpServletResponse response)
			throws ContentHandlerException {
		
		try {
			if (log.isDebugEnabled()) {
				log.debug(String.format("SHR GET documents request for patient %s-%s", patientIdType, patientId));
			}
			
			if (StringUtils.isBlank(contentType))
				throw new RequestError(HttpStatus.BAD_REQUEST.value(), "Content-Type expected");
			if (StringUtils.isBlank(uniqueID))
				uniqueID = UUID.randomUUID().toString();

			Patient patient = getOrCreatePatient(patientId, patientIdType);
			Provider provider = getOrCreateProvider(providerId, providerIdType);
			EncounterRole role = getDefaultEncounterRole();
			EncounterType type = getOrCreateEncounterType(encounterType);
			boolean url = (isURL!=null && "true".equalsIgnoreCase(isURL)) ? true : false;
			CodedValue typeCode = new CodedValue(typeCodeCode, typeCodeCodingScheme, typeCodeCodingScheme);
			CodedValue formatCode = new CodedValue(formatCodeCode, formatCodeCodingScheme, formatCodeCodingScheme);

			Content content = buildContent(uniqueID, contentType, request, typeCode, formatCode, url);
			
			ContentHandlerService chs = Context.getService(ContentHandlerService.class);
			ContentHandler handler = chs.getContentHandler(contentType);

			Map<EncounterRole, Set<Provider>> providersByRole = new HashMap<>();
			providersByRole.put(role, new HashSet<>(Arrays.asList(provider))); // only one element
			Encounter encounter = handler.saveContent(patient, providersByRole, type, content);
			
			//TODO associate encounter with uniqueID
			
			response.setStatus(HttpStatus.CREATED.value());
			log.debug("CREATED");
			return encounter.getUuid();
			
		} catch (RequestError error) {
			if (log.isDebugEnabled()) {
				log.debug("Request Response - " + error);
			}
			response.setStatus(error.responseCode);
			return error.response;
		}
	}
	
	@RequestMapping(value = "/document", method = RequestMethod.GET)
	@ResponseBody
	public Object getDocument(
			@RequestParam(value = "contentType", required = true) String contentType,
			@RequestParam(value = "encounterUUID", required = false) String encounterUUID,
			@RequestParam(value = "uniqueID", required = false) String uniqueID,
			HttpServletRequest request, HttpServletResponse response)
			throws ContentHandlerException {
		
		try {
			if (StringUtils.isBlank(encounterUUID) && StringUtils.isBlank(uniqueID))
				throw new RequestError(HttpStatus.BAD_REQUEST.value(), "Either encounterUUID or uniqueID must be specified");
			if (!StringUtils.isBlank(encounterUUID) && !StringUtils.isBlank(uniqueID))
				throw new RequestError(HttpStatus.BAD_REQUEST.value(), "encounterUUID and uniqueID cannot both be specified");
			
			if (!StringUtils.isBlank(uniqueID))
				throw new RequestError(HttpStatus.BAD_REQUEST.value(), "Query by uniqueID not implemented yet");
			
			ContentHandlerService chs = Context.getService(ContentHandlerService.class);
			ContentHandler handler = chs.getContentHandler(contentType);
			
			Content content = handler.fetchContent(encounterUUID);
			
			response.setStatus(HttpStatus.OK.value());
			return content;
			
		} catch (RequestError error) {
			if (log.isDebugEnabled()) {
				log.debug("Request Response - " + error);
			}

			response.setStatus(error.responseCode);
			return error.response;
		}
	}
	
	@RequestMapping(value = "/documents", method = RequestMethod.GET)
	@ResponseBody
	public Object getDocuments(
			@RequestParam(value = "contentType", required = true) String contentType,
			@RequestParam(value = "patientId", required = true) String patientId,
			@RequestParam(value = "patientIdType", required = true) String patientIdType,
			@RequestParam(value = "dateStart", required = false) String dateStart,
			@RequestParam(value = "dateEnd", required = false) String dateEnd,
			HttpServletRequest request, HttpServletResponse response) {
		
		try {
			if (log.isDebugEnabled()) {
				log.debug(String.format("SHR GET documents request for patient %s-%s", patientIdType, patientId));
			}
			
			Patient patient = getPatient(patientId, patientIdType);
			if (patient==null) {
				String msg = String.format("Patient %s-%s not found", patientIdType, patientId);
				throw new RequestError(HttpStatus.NOT_FOUND.value(), msg);
			}
			
			Date from = parseDate(dateStart);
			Date to = parseDate(dateEnd);
			List<Content> result = getContent(contentType, patient, from, to);
			
			response.setStatus(HttpStatus.OK.value());
			
			log.debug("OK");
			return result.isEmpty() ? null : result;
			
		} catch (RequestError error) {
			if (log.isDebugEnabled()) {
				log.debug("Request Response - " + error);
			}
				
			response.setStatus(error.responseCode);
			return error.response;
		}
	}
	
	private Date parseDate(String date) throws RequestError {
		try {
			if (date==null || date.isEmpty())
				return null;
			
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			return format.parse(date);
		} catch (ParseException ex) {
			throw new RequestError(HttpStatus.BAD_REQUEST.value(), "Invalid date format. ISO 8601 expected.");
		}
	}
	
	private Patient getPatient(String patientId, String idType) throws RequestError {
		PatientService ps = Context.getPatientService();
		PatientIdentifierType pidType = ps.getPatientIdentifierTypeByName(idType);
		
		if (pidType==null) {
			//Patient can't have a particular id if its id type doesn't exist
			return null;
		}
			
		List<Patient> patients = ps.getPatients(null, patientId, Collections.singletonList(pidType), true);
		
		if (patients.isEmpty()) {
			return null;
		} else if (patients.size()>1) {
			String message = String.format("Multiple patients found for identifier %s-%s", idType, patientId);
			log.error(message);
			throw new RequestError(HttpStatus.INTERNAL_SERVER_ERROR.value(), message);
		}
		
		return patients.get(0);
	}
	
	private Patient getOrCreatePatient(String patientId, String idType) throws RequestError {
		Patient p = getPatient(patientId, idType);
		if (p==null) {
			PatientIdentifierType pidType = Context.getPatientService().getPatientIdentifierTypeByName(idType);
			if (pidType==null) {
				pidType = new PatientIdentifierType();
				pidType.setName(idType);
				pidType.setDescription("OpenHIE SHR generated patient identifier type for '" + idType + "'");
				Context.getPatientService().savePatientIdentifierType(pidType);
				log.info("Created patient identifier type '" + idType + "'");
			}
			return createPatient(patientId, pidType);
		}
		return p;
	}
	
	private Patient createPatient(String id, PatientIdentifierType idType) {
		Patient patient = new Patient();
		
		PatientIdentifier pi = new PatientIdentifier();
        pi.setIdentifierType(idType);
        pi.setIdentifier(id);
        pi.setLocation(Context.getLocationService().getDefaultLocation());
        pi.setPreferred(true);
        patient.addIdentifier(pi);

        //TODO How should we handle names?
        PersonName pn = new PersonName();
        pn.setGivenName("Auto");
        pn.setFamilyName("Generated");
        patient.addName(pn);
        
        //TODO Gender is required, but we don't know this...
        patient.setGender("F");

        Context.getPatientService().savePatient(patient);
		return patient;
	}
	
	private Provider getOrCreateProvider(String id, String idType) throws RequestError {
		ProviderService ps = Context.getProviderService();
		ProviderAttributeType type = getIdTypeAsProviderAttributeType(idType);
		Map<ProviderAttributeType, Object> attr = new HashMap<ProviderAttributeType, Object>();
		
		attr.put(type, id);
		List<Provider> providers = ps.getProviders(null, null, null, attr);
		
		if (providers.isEmpty()) {
			return createProvider(id, type);
		} else if (providers.size()>1) {
			String message = String.format("Multiple providers found for identifier %s-%s", idType, id);
			log.error(message);
			throw new RequestError(HttpStatus.INTERNAL_SERVER_ERROR.value(), message);
		}
		
		return providers.get(0);
	}
	
	private ProviderAttributeType getIdTypeAsProviderAttributeType(String idType) {
		ProviderService ps = Context.getProviderService();
		
		for (ProviderAttributeType type : ps.getAllProviderAttributeTypes()) {
			if (idType.equals(type.getName()))
				return type;
		}
		
		ProviderAttributeType type = new ProviderAttributeType();
		type.setName(idType);
		type.setDatatypeClassname("org.openmrs.customdatatype.datatype.FreeTextDatatype");
		type.setDescription("OpenHIE SHR generated provider attribute type for '" + idType + "'");
		ps.saveProviderAttributeType(type);
		log.info("Created provider attribute type '" + idType + "'");
		return type;
	}
	
	private Provider createProvider(String id, ProviderAttributeType idType) {
		Provider p = new Provider();
		p.setName("Auto-generated provider");
		ProviderAttribute pa = new ProviderAttribute();
		pa.setAttributeType(idType);
		pa.setValue(id);
		p.addAttribute(pa);
		Context.getProviderService().saveProvider(p);
		return p;
	}
	
	private EncounterType getOrCreateEncounterType(String encounterTypeName) {
		EncounterService es = Context.getEncounterService();
		EncounterType type = es.getEncounterType(encounterTypeName);
		if (type==null) {
			type = new EncounterType(encounterTypeName, "Created by the OpenHIE SHR");
			es.saveEncounterType(type);
		}
		return type;
	}
	
	@SuppressWarnings("deprecation")
	private EncounterRole getDefaultEncounterRole() {
		String uuid = Context.getAdministrationService().getGlobalProperty(ENCOUNTERROLE_UUID_GLOBAL_PROP);
		EncounterRole encounterRole = Context.getEncounterService().getEncounterRoleByUuid(uuid);
		
		if(encounterRole == null) {
			encounterRole = new EncounterRole();
			encounterRole.setName("Default Encounter Role");
			encounterRole.setDescription("Created by the OpenHIE SHR");
			
			encounterRole = Context.getEncounterService().saveEncounterRole(encounterRole);
			Context.getAdministrationService().setGlobalProperty(ENCOUNTERROLE_UUID_GLOBAL_PROP, encounterRole.getUuid());
		} 
    
		return encounterRole;
	}
	
	
	private List<Content> getContent(String contentType, Patient patient, Date from, Date to) throws RequestError {
		ContentHandlerService chs = Context.getService(ContentHandlerService.class);
		ContentHandler handler = chs.getContentHandler(contentType);
		List<Content> res = new LinkedList<Content>();

		EncounterSearchCriteriaBuilder builder = new EncounterSearchCriteriaBuilder();
		builder.setPatient(patient);
		builder.setFromDate(from);
		builder.setToDate(to);

		List<Encounter> encs = Context.getEncounterService().getEncounters(builder.createEncounterSearchCriteria());

		try {
			for (Encounter enc : encs) {
				res.add(handler.fetchContent(enc.getUuid()));
			}
		} catch (Exception e) {
			log.error(e);
			throw new RequestError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error while processing request: " + e.getMessage());
		}
		
		return res;
	}

	private Content buildContent(String uniqueId, String contentType, HttpServletRequest request, CodedValue typeCode, CodedValue formatCode, boolean isURL) throws RequestError {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			IOUtils.copy(request.getInputStream(), out);
			
			String payload = null;
			Representation rep = null;
			if (isContentTypeTextBased(contentType)) {
				//This will include text-based URL data
				payload = new String(out.toByteArray());
				rep = Representation.TXT;
			} else if (isURL) {
				payload = new String(out.toByteArray());
				rep = Representation.BINARY;
			} else {
				payload = Base64.encodeBase64String(out.toByteArray());
				rep = Representation.B64;
			}
			return new Content(uniqueId, payload.getBytes(), isURL, typeCode, formatCode, request.getContentType(), request.getCharacterEncoding(), rep, null, null);
		} catch (IOException ex) {
			throw new RequestError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error while processing request: " + ex.getMessage());
		}
	}
	
	private static boolean isContentTypeTextBased(String contentType) {
		return contentType.startsWith("text") ||
			//try to match application/xml* (e.g. application/xml+cda)
			contentType.startsWith("application/xml") ||
			//try to match application/json*
			contentType.startsWith("application/json");
	}
	
	
	@SuppressWarnings("serial")
	private static class RequestError extends Exception {
		int responseCode;
		String response;
		
		public RequestError(int responseCode, String response) {
			this.responseCode = responseCode;
			this.response = response;
		}

		@Override
		public String toString() {
			return String.format("Code: %s, Response: %s", responseCode, response);
		}
	}
}
