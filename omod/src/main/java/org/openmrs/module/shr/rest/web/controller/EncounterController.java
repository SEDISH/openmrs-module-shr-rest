package org.openmrs.module.shr.rest.web.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.shr.contenthandler.api.Content.Representation;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
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
			@RequestParam(value = "formatCode", required = true) String formatCode,
			HttpServletRequest request, HttpServletResponse response) {
		
		try {
			Patient patient = getOrCreatePatient(patientId, patientIdType);
			Provider provider = getOrCreateProvider(providerId, providerIdType);
			EncounterRole role = getDefaultEncounterRole();
			EncounterType type = getOrCreateEncounterType(encounterType);
			Content content = buildContent(request, formatCode);
			
			ContentHandlerService chs = Context.getService(ContentHandlerService.class);
			ContentHandler handler = chs.getContentHandler(contentType);
			handler.saveContent(patient, provider, role, type, content);
			
			response.setStatus(HttpStatus.CREATED.value());
			return null;
			
		} catch (RequestError error) {
			response.setStatus(error.responseCode);
			if (error.responseType!=null)
				response.setContentType(error.responseType);
			return error.response + "\n";
		}
	}
	
	@RequestMapping(value = "/documents", method = RequestMethod.GET)
	@ResponseBody
	public Object getDocuments(
			@RequestHeader(value = "Accept", required = true) String accept,
			@RequestParam(value = "patientId", required = true) String patientId,
			@RequestParam(value = "patientIdType", required = true) String patientIdType,
			@RequestParam(value = "dateStart", required = false) String dateStart,
			@RequestParam(value = "dateEnd", required = false) String dateEnd,
			HttpServletRequest request, HttpServletResponse response) {
		
		try {
			Patient patient = getOrCreatePatient(patientId, patientIdType);
			Date from = parseDate(dateStart);
			Date to = parseDate(dateEnd);
			List<byte[]> result = getContent(accept, patient, from, to);
			
			response.setStatus(HttpStatus.OK.value());
			response.setContentType(accept);
			
			return result.isEmpty() ? null : result;
			
		} catch (RequestError error) {
			response.setStatus(error.responseCode);
			if (error.responseType!=null)
				response.setContentType(error.responseType);
			return error.response + "\n";
		}
	}
	
	private Date parseDate(String date) throws RequestError {
		try {
			if (date==null || date.isEmpty())
				return null;
			
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			return format.parse(date);
		} catch (ParseException ex) {
			throw new RequestError(HttpStatus.BAD_REQUEST.value(), "text/plain", "Invalid date format. ISO 8601 expected.");
		}
	}
	
	private Patient getOrCreatePatient(String patientId, String idType) throws RequestError {
		PatientService ps = Context.getPatientService();
		PatientIdentifierType pidType = ps.getPatientIdentifierTypeByName(idType);
		
		if (pidType==null)
			throw new RequestError(HttpStatus.NOT_FOUND.value(), "text/plain", "Unknown identifier type '" + idType + "'");
			
		List<Patient> patients = ps.getPatients(null, patientId, Collections.singletonList(pidType), true);
		
		if (patients.isEmpty())
			return createPatient(patientId, pidType);
		if (patients.size()>1)
			throw new RequestError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "text/plain", "Multiple patients found for requested identifier");
		
		return patients.get(0);
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

		return patient;
	}
	
	private Provider getOrCreateProvider(String id, String idType) throws RequestError {
		ProviderService ps = Context.getProviderService();
		ProviderAttributeType type = getIdTypeAsProviderAttributeType(idType);
		Map<ProviderAttributeType, Object> attr = new HashMap<ProviderAttributeType, Object>();
		
		attr.put(type, id);
		List<Provider> providers = ps.getProviders(null, null, null, attr);
		
		if (providers.isEmpty())
			return createProvider(id, type);
		else if (providers.size()>1)
			throw new RequestError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "text/plain", "Multiple providers found for requested identifier");
		
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
		ps.saveProviderAttributeType(type);
		return type;
	}
	
	private Provider createProvider(String id, ProviderAttributeType idType) {
		Provider p = new Provider();
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
	
	
	private List<byte[]> getContent(String contentType, Patient patient, Date from, Date to) throws RequestError {
		ContentHandlerService chs = Context.getService(ContentHandlerService.class);
		ContentHandler handler = chs.getContentHandler(contentType);
		List<byte[]> res = new LinkedList<byte[]>();
		
		try {
			for (Content content : handler.queryEncounters(patient, from, to)) {
				res.add(content.getRawData());
			}
		} catch (IOException e) {
			log.error(e);
			throw new RequestError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "text/plain", "Error while processing request: " + e.getMessage());
		}
		
		return res;
	}
	
	private Content buildContent(HttpServletRequest request, String formatCode) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			IOUtils.copy(request.getInputStream(), out);
			String payload = Base64.encodeBase64String(out.toByteArray());
			return new Content(payload, false, formatCode, request.getContentType(), request.getCharacterEncoding(), Representation.B64, null, null);
		} catch (IOException ex) {
			return null;
		}
	}
	
	@SuppressWarnings("serial")
	private static class RequestError extends Exception {
		int responseCode;
		String responseType;
		String response;
		
		public RequestError(int responseCode, String responseType, String response) {
			this.responseCode = responseCode;
			this.responseType = responseType;
			this.response = response;
		}
	}
}
