package gov.va.akcds.export;

import gov.va.akcds.export.util.ConsoleUtil;
import gov.va.akcds.export.util.StatsFilePrinter;

import java.io.File;
import java.io.IOException;
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
	public static final UUID akcdsFactsRelParentUUID_ = UUID.nameUUIDFromBytes(("gov.va.spl:metadata:types:draftFactsRelationships").getBytes());
	
	public static final UUID moduleID_ = UUID.nameUUIDFromBytes(("gov.va.spl:derivedFacts").getBytes());
	
	public int akcdsFactsRelParentNid_;
	
	NidBitSetBI allConcepts;
	private File outputDirectory_;
	private long scannedConcepts_ = 0;
	private long AKCDSRels_ = 0;
	
	private StatsFilePrinter delimitedOutput_;
	private StatsFilePrinter refSetOutput_;
	

	public ConceptProcessor(File outputDirectory) throws IOException
	{
		allConcepts = Ts.get().getAllConceptNids();
		akcdsFactsRelParentNid_ = Ts.get().getConcept(akcdsFactsRelParentUUID_).getConceptNid();
		outputDirectory_ = outputDirectory;
		delimitedOutput_ = new StatsFilePrinter(new String[] {"NDF concept", "Relation concept", "Snomed Target Concept"}, "\t", "\r\n", 
					new File(outputDirectory_, "SPLFactExport.tsv"), "SPL Delimited Fact Export");
		refSetOutput_ = new StatsFilePrinter(new String[] {"id", "effectiveTime", "active", "moduleId", "refSetId", "referencedComponentId", "targetComponentId"},
				"\t", "\r\n", new File(outputDirectory_, "refSetExport.tsv"), "RF2 Refset Export");
	}
	
	public void shutdown() throws IOException
	{
		delimitedOutput_.close();
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
		//member UUID, collection UUID, referenced component UUID, Module UUID, Status UUID, Path UUID, Time, nid 1 uuid, nid 2 uuid
		
		delimitedOutput_.addLine(new String[] {
				c.toString(), 
				Ts.get().getConcept(rel.getTypeNid()).toString(), 
				Ts.get().getConcept(rel.getDestinationNid()).toString()});
		
		//"id", "effectiveTime", "active", "moduleId", "refSetId", "referencedComponentId", "targetComponentId"
		refSetOutput_.addLine(new String[] {  
				rel.getPrimUuid().toString(), 
				rel.getTime() + "", 
				"true", 
				moduleID_.toString(),
				Ts.get().getConcept(rel.getTypeNid()).getPrimUuid().toString(),
				c.getPrimUuid().toString(),
				Ts.get().getConcept(rel.getDestinationNid()).getPrimUuid().toString()});
				
	}
}
