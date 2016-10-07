package gov.ca.emsa.service.controller;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import javax.xml.bind.JAXBException;
import javax.xml.soap.SOAPException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.opensaml.common.SAMLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import gov.ca.emsa.service.EHealthQueryConsumerService;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;

@RestController
public class DocumentQueryController {
	private static final Logger logger = LogManager.getLogger(DocumentQueryController.class);
	private static final String RESOURCE_FILE_NAME = "ValidDocumentQueryResponse.xml";
	private static final int MIN_DOCUMENTS = 1;
	private static final int MAX_DOCUMENTS = 2;
	
	@Autowired private ResourceLoader resourceLoader;
	@Autowired EHealthQueryConsumerService consumerService;

	@Value("${minimumResponseSeconds}")
	private String minimumResponseSeconds;
	
	@Value("${maximumResponseSeconds}")
	private String maximumResponseSeconds;
	
	@RequestMapping(value = "/documentQuery", method = RequestMethod.POST, produces = MediaType.APPLICATION_XML_VALUE)
	public String queryRequest(@RequestBody String request) throws InterruptedException {
		try{
			AdhocQueryRequest requestObj = consumerService.unMarshallDocumentQueryRequestObject(request);
		}catch(SAMLException e){
			return consumerService.createSOAPFault();
		}

		int numDocsToReturn = ThreadLocalRandom.current().nextInt(MIN_DOCUMENTS, MAX_DOCUMENTS + 1);
		String result = "";
		try {
			Resource documentsFile = resourceLoader.getResource("classpath:" + RESOURCE_FILE_NAME);
			AdhocQueryResponse response = consumerService.unMarshallDocumentQueryResponseObject(Resources.toString(documentsFile.getURL(), Charsets.UTF_8));

			//adjust based on num docs
			if(numDocsToReturn == 1) {
				RegistryObjectListType regList = response.getRegistryObjectList();
				regList.getIdentifiable().remove(1);	
			}

			result = consumerService.marshallDocumentQueryResponse(response);
		} catch (IOException |SAMLException | SOAPException | JAXBException e) {
			logger.error(e);
			throw new HttpMessageNotWritableException("Could not read response file");
		}
		
		try {	
			int minSeconds = new Integer(minimumResponseSeconds.trim()).intValue();
			int maxSeconds = new Integer(maximumResponseSeconds.trim()).intValue();
			int responseIntervalSeconds = ThreadLocalRandom.current().nextInt(minSeconds, maxSeconds + 1);
			logger.info("Sleeping for " + responseIntervalSeconds + " seconds");
			Thread.sleep(responseIntervalSeconds*1000);
			return result;
		} catch(InterruptedException inter) {
			logger.error("Interruped!", inter);
			throw inter;
		}	
	}
}
