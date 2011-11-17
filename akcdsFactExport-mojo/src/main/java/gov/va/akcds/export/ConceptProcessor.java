package gov.va.akcds.export;

import gov.va.akcds.export.util.ConsoleUtil;
import gov.va.akcds.export.util.RF2FileWriter;
import gov.va.akcds.export.util.VerhoeffCheck;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.dwfa.ace.api.I_GetConceptData;
import org.dwfa.ace.api.I_RelVersioned;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.ConceptFetcherBI;
import org.ihtsdo.tk.api.NidBitSetBI;
import org.ihtsdo.tk.api.ProcessUnfetchedConceptDataBI;
import org.ihtsdo.tk.api.concept.ConceptChronicleBI;
import org.ihtsdo.tk.api.relationship.RelationshipAnalogBI;
import org.ihtsdo.tk.api.relationship.RelationshipChronicleBI;

public class ConceptProcessor implements ProcessUnfetchedConceptDataBI
{
	//This comes from the workbench loader code
	public static final UUID akcdsFactsRelParentUUID_ = UUID.nameUUIDFromBytes(("gov.va.spl:metadata:types:draftFactsRelationships").getBytes());
	
	//made up.  This should be a 7 digit unique number, but I'm not sure what value to use.  Keith says he doesn't care about the namespace, 
	//so I'm leaving it as is - so it continues to generate valid RF2 (or at least, as close as we can get with the UUID substitutions in the rel file)
	public static final String namespaceIdentifier_ = "2003445";  
	
	//This is generated in the createRF2 metadata method.
	private static String createdModuleSCTId_ = "";  
	
	//format required for RF2 time values
	private static final SimpleDateFormat sdf_ = new SimpleDateFormat("yyyyMMdd");
	String timeStamp_ = sdf_.format(new Date(System.currentTimeMillis()));
	
	private int akcdsFactsRelParentNid_;
	
	private NidBitSetBI allConcepts;
	private File outputDirectory_;
	private long scannedConcepts_ = 0;
	private long AKCDSRels_ = 0;
	
	private RF2FileWriter delimitedOutput_;
	private RF2FileWriter refSetOutput_;
	private RDF rdf_;
	

	public ConceptProcessor(File outputDirectory) throws IOException, URISyntaxException
	{
		rdf_ = new RDF();
		allConcepts = Ts.get().getAllConceptNids();
		akcdsFactsRelParentNid_ = Ts.get().getConcept(akcdsFactsRelParentUUID_).getConceptNid();
		outputDirectory_ = outputDirectory;
		createRF2MetaData();
		delimitedOutput_ = new RF2FileWriter(new String[] {
				"member (relationship type) UUID",   //workbench relationship item
				"collection UUID",  //akcdsFactsRelParentUUID_
				"Referenced Component (Source) UUID",
				"Module UUID", //generated module ID - converted to UUID
				"Status UUID",  //status from workbench
				"Path UUID",  //path from workbench
				"Time",  //time from workbench
				"Relationship Type UUID", //relType from workbench
				"Target Component UUID"},
					new File(outputDirectory_, "SPLFactExport.tsv"), "SPL Delimited Fact Export");
		
		refSetOutput_ = new RF2FileWriter(new String[] {
				"id", 
				"effectiveTime", 
				"active", 
				"moduleId", 
				"refSetId", 
				"referencedComponentId", 
				"targetComponentId"},
				new File(outputDirectory_, "der2_Refset_AssociationReferenceSnapshot_" + namespaceIdentifier_ + "_" + timeStamp_ + ".tsv"),
				"RF2 Refset File");
	}
	
	public void shutdown() throws IOException
	{
		rdf_.export(outputDirectory_);
		delimitedOutput_.close();
		ConsoleUtil.println("Writing RDF File");
		refSetOutput_.close();
		ConsoleUtil.println("Viewed " + scannedConcepts_ + " concepts");
		ConsoleUtil.println("Found " + AKCDSRels_ + " AKCDS facts");
	}

	@Override
	public void processUnfetchedConceptData(int cNid, ConceptFetcherBI fetcher) throws Exception
	{
		I_GetConceptData c = (I_GetConceptData) fetcher.fetch();
		handleConcept(c);
	}

	@Override
	public NidBitSetBI getNidSet() throws IOException
	{
		return allConcepts;
	}

	@Override
	public boolean continueWork()
	{
		return true;
	}
	
	private void handleConcept(I_GetConceptData c) throws IOException
	{
		scannedConcepts_++;
		if (scannedConcepts_ % 500 == 0)
		{
			ConsoleUtil.showProgress();
		}
		if (scannedConcepts_ % 100000 == 0)
		{
			ConsoleUtil.println("Processed " + scannedConcepts_);
		}
		
		for (I_RelVersioned<RelationshipAnalogBI<?>> rel : c.getSourceRels())
		{
			ConceptChronicleBI relType = Ts.get().getConcept(rel.getTypeNid());
			for (RelationshipChronicleBI parentRel : relType.getRelsOutgoing())
			{
				if (parentRel.getDestinationNid() == akcdsFactsRelParentNid_)
				{
					handleDraftFactConcept(c, rel);
				}
			}
		}
	}
	
	private void handleDraftFactConcept(I_GetConceptData c, I_RelVersioned<RelationshipAnalogBI<?>> rel) throws IOException
	{
		AKCDSRels_++;
		//See creation of delimitedOutput for more details on fields.
		delimitedOutput_.addLine(new String[] {
				rel.getPrimUuid().toString(),  
				akcdsFactsRelParentUUID_.toString(),  
				c.getPrimUuid().toString(),
				UUID.nameUUIDFromBytes((createdModuleSCTId_).getBytes()).toString(), 
				Ts.get().getConcept(rel.getStatusNid()).getPrimUuid().toString(),
				Ts.get().getConcept(rel.getPathNid()).getPrimUuid().toString(),
				sdf_.format(new Date(rel.getTime())),
				Ts.get().getConcept(rel.getTypeNid()).getPrimUuid().toString(),
				Ts.get().getConcept(rel.getDestinationNid()).getPrimUuid().toString()});
		
		//"id", "effectiveTime", "active", "moduleId", "refSetId", "referencedComponentId", "targetComponentId"
		refSetOutput_.addLine(new String[] {  
				rel.getPrimUuid().toString(), 
				sdf_.format(new Date(rel.getTime())),
				"1",   //true
				createdModuleSCTId_,
				Ts.get().getConcept(rel.getTypeNid()).getPrimUuid().toString(),
				c.getPrimUuid().toString(),
				Ts.get().getConcept(rel.getDestinationNid()).getPrimUuid().toString()});
			
		//Note the last three fields here are specified as SctId types in the RF2 spec.  But we are using UUID's - per Keith's say-so.
		//So, it isn't valid RF2, but fairly close.
		
		rdf_.addConcept(c, rel);
	}
	
	@SuppressWarnings("unused")
	private void createRF2MetaData() throws IOException
	{
		//A bunch of constants from snomed specs and/or current data
		String extensionConcept = "10";
		String extensionDescription = "11";
		String extensionRelationship = "12";
		
		String moduleSCTID = "900000000000443000"; //Module
		String modelComponentModuleID = "900000000000012004";  //SNOMED CT model component module (core metadata concept) 
		String definitionStatusIdSufficient = "900000000000073002"; // Sufficiently defined concept definition status
		String definistionStatusIdNotSufficient = "900000000000074008"; // Necessary but not sufficient concept definition status 
		
		String isA = "116680003";
		String statedRel = "900000000000010007";
		String inferredRel = "900000000000011006";
		String relTypeSome = "900000000000451002";
		String relTypeAll = "900000000000452009";
		
		String descTypeFSN = "900000000000003001"; //Fully specified Name
		String descTypeDefinition = "900000000000550004";  //Definition
		String descTypeSyn = "900000000000013009"; //Synonym
	
		String caseInsensitive = "900000000000448009";
		String caseSensitive = "900000000000017005";
		String caseInitialCharSensitive = "900000000000020002";
		
		String associationTypeReferenceSet = "900000000000521006";
		
		int uniqueId = 1001;
		
		RF2FileWriter concepts = new RF2FileWriter(new String[] {"id", 
				"effectiveTime", 
				"active", 
				"moduleId", 
				"definitionStatusId"}, 
				new File(outputDirectory_, "sct_Concept_Snapshot_" + namespaceIdentifier_ + "_" + timeStamp_ + ".tsv"), 
				"RF2 Concept File");
		
		RF2FileWriter relationship = new RF2FileWriter(new String[] {"id", 
				"effectiveTime", 
				"active", 
				"moduleId", 
				"sourceId", 
				"destinationId", 
				"relationshipGroup", 
				"typeId", 
				"characteristicTypeId", 
				"modifierId"},
				new File(outputDirectory_, "sct_Relationship_Snapshot_" + namespaceIdentifier_ + "_" + timeStamp_ + ".tsv"),
				"RF2 Relationship File");
		
		RF2FileWriter description = new RF2FileWriter(new String[] {"id", 
				"effectiveTime", 
				"active", 
				"moduleId", 
				"conceptId", 
				"languageCode", 
				"typeId", 
				"term", 
				"caseSignificanceId"},
				new File(outputDirectory_, "sct_Description_Snapshot_" + namespaceIdentifier_ + "_" + timeStamp_ + ".tsv"),
				"RF2 Description File");
		/*
		 * Need create a new concept to store our moduleConceptId.
		 */  
		 
		createdModuleSCTId_ = addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionConcept);
		
		concepts.addLine(new String[] {createdModuleSCTId_, 
				timeStamp_,
				"1",
				createdModuleSCTId_,  //Yes, this is circular logic.  But John Gutai told me this is the correct way.
				definistionStatusIdNotSufficient});
		
		
		//And create the relationship for the concept
		relationship.addLine(new String[] {addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionRelationship),
				timeStamp_,
				"1",
				createdModuleSCTId_,
				moduleSCTID,
				createdModuleSCTId_,
				"0",
				isA,
				statedRel,
				relTypeSome});
		
		//Add add the descriptions
		description.addLine(new String[] {addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionDescription),
				timeStamp_,
				"1",
				createdModuleSCTId_,  
				createdModuleSCTId_,
				"en",
				descTypeFSN,
				"AKCDS Module Identifier Metadata Concept",
				caseSensitive});
		description.addLine(new String[] {addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionDescription),
				timeStamp_,
				"1",
				createdModuleSCTId_,
				createdModuleSCTId_,
				"en",
				descTypeSyn,
				"AKCDS Module Identifier Metadata Concept",
				caseSensitive});
		
		/*
		 * Now create a concept to represent our refset
		 */
		
		String refSetConceptId = addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionConcept);
		concepts.addLine(new String[] {refSetConceptId, 
				timeStamp_,
				"1",
				createdModuleSCTId_,  
				definistionStatusIdNotSufficient});
		
		//Create the descriptions for the refset
		description.addLine(new String[] {addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionDescription),
				timeStamp_,
				"1",
				createdModuleSCTId_,  
				refSetConceptId,
				"en",
				descTypeFSN,
				"AKCDS RXNorm to Snomed relationships refset",
				caseSensitive});
		description.addLine(new String[] {addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionDescription),
				timeStamp_,
				"1",
				createdModuleSCTId_,  
				refSetConceptId,
				"en",
				descTypeSyn,
				"AKCDS RXNorm to Snomed relationships refset",
				caseSensitive});
		description.addLine(new String[] {addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionDescription),
				timeStamp_,
				"1",
				createdModuleSCTId_,  
				refSetConceptId,
				"en",
				descTypeDefinition,
				"Facts generated from Drug labels which link drugs from RXNorm to concepts in Snomed",
				caseSensitive});
		
		//And add the linking relationship
		relationship.addLine(new String[] {addCheckDigit(uniqueId++ + namespaceIdentifier_ + extensionRelationship),
				timeStamp_,
				"1",
				createdModuleSCTId_,  
				refSetConceptId,
				associationTypeReferenceSet,
				"0",
				isA,
				statedRel,
				relTypeSome});
		
		/*
		 * That should cover the metadata we need to add.
		 */
		
		concepts.close();
		relationship.close();
		description.close();
	}
	
	private String addCheckDigit(String value)
	{
		return value + VerhoeffCheck.generateVerhoeff(value);
	}
}
