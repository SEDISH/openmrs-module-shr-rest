package org.openmrs.module.shr.rest.web.controller;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value="rest/shr")
public class EncounterController {
	
	Log log = LogFactory.getLog(this.getClass());
	
	
	@RequestMapping(value = "/documents", method = RequestMethod.GET)
	@ResponseBody
	public Object getEncounters(
			@RequestParam(value = "patientId", required = true) String patientId,
			@RequestParam(value = "idType", required = true) String idType,
			@RequestParam(value = "dateStart", required = false) String dateStart,
			@RequestParam(value = "dateEnd", required = false) String dateEnd,
			HttpServletRequest request, HttpServletResponse response) {
		
		try {
			String contentType = request.getContentType();
			if (contentType==null) {
				throw new RequestError(406, "text/plain", "Content-Type Required");
			}
			
			Date from = parseDate(dateStart);
			Date to = parseDate(dateEnd);
			Patient patient = getPatient(patientId, idType);
			List<byte[]> result = getContent(contentType, patient, from, to);
			
			response.setStatus(200);
			response.setContentType(contentType);
			
			return result.isEmpty() ? null : result;
			
		} catch (RequestError error) {
			response.setStatus(error.responseCode);
			if (error.responseType!=null)
				response.setContentType(error.responseType);
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
			throw new RequestError(400, "text/plain", "Invalid date format. ISO 8601 expected.");
		}
	}
	
	private Patient getPatient(String patientId, String idType) throws RequestError {
		PatientService ps = Context.getPatientService();
		PatientIdentifierType pid = ps.getPatientIdentifierTypeByName(idType);
		
		if (pid==null)
			throw new RequestError(404, "text/plain", "Unknown identifier type '" + idType + "'");
			
		List<Patient> patients = ps.getPatients(null, patientId, Collections.singletonList(pid), true);
		
		if (patients.isEmpty())
			throw new RequestError(404, "text/plain", "Requested patient not found");
		if (patients.size()>1)
			throw new RequestError(500, "text/plain", "Multiple patients match requested identifier");
		
		return patients.get(0);
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
			throw new RequestError(500, "text/plain", "Error while processing request: " + e.getMessage());
		}
		
		return res;
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
