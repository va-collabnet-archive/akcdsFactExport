package gov.va.akcds.export;

import gov.va.akcds.export.util.ConsoleUtil;
import gov.va.akcds.export.util.RF2FileWriter;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.UUID;

import org.dwfa.ace.api.I_GetConceptData;
import org.dwfa.ace.api.I_RelVersioned;
import org.ihtsdo.concept.component.refsetmember.cidCidCid.CidCidCidMember;
import org.ihtsdo.concept.component.refsetmember.str.StrMember;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.ConceptFetcherBI;
import org.ihtsdo.tk.api.NidBitSetBI;
import org.ihtsdo.tk.api.ProcessUnfetchedConceptDataBI;
import org.ihtsdo.tk.api.concept.ConceptChronicleBI;
import org.ihtsdo.tk.api.refex.RefexChronicleBI;
import org.ihtsdo.tk.api.relationship.RelationshipChronicleBI;

public class SPLConceptProcessor implements ProcessUnfetchedConceptDataBI
{
	//This comes from the workbench loader code
	public static final UUID akcdsFactsRelParentUUID_ = UUID.nameUUIDFromBytes(("gov.va.spl:metadata:types:draftFactsRelationships").getBytes());
	private NidBitSetBI allConcepts;
	private File outputDirectory_;
	private RF2FileWriter delimitedOutput_;
	private long scannedConcepts_ = 0;
	
	UUID DRAFT_FACT_TRIPLE = getSPLConstantUUID("Draft Fact Triple");
	UUID DRAFT_FACT_SNOMED_CONCEPT_NAME = getSPLConstantUUID("Snomed Concept Name");
	UUID DRAFT_FACT_SNOMED_CONCEPT_CODE = getSPLConstantUUID("Snomed Concept Code");
	UUID DRAFT_FACT_CURATION_STATE = getSPLConstantUUID("Curation state");
	UUID DRAFT_FACT_SET_ID = getSPLConstantUUID("Draft Fact Set ID");
	UUID DRAFT_FACT_UNIQUE_ID = getSPLConstantUUID("Draft Fact Unique ID");
	UUID ndaTypeRoot = UUID.nameUUIDFromBytes(("gov.va.spl:metadata:types:ndaTypes").getBytes());
	UUID SPL_SET_ID = getSPLConstantUUID("Set ID");
	
	int draftFactTripleNid;
	int draftFactSnomedConceptNameNid;
	int draftFactSnomedConceptCode;
	int draftFactCurationState;
	int draftFactSetId;
	int draftFactUniqueId;
	int ndaTypeRootId;
	int splSetId;

	public SPLConceptProcessor(File outputDirectory) throws IOException, URISyntaxException
	{
		allConcepts = Ts.get().getAllConceptNids();
		draftFactTripleNid = Ts.get().getConcept(DRAFT_FACT_TRIPLE).getNid();
		draftFactSnomedConceptNameNid = Ts.get().getConcept(DRAFT_FACT_SNOMED_CONCEPT_NAME).getNid();
		draftFactSnomedConceptCode = Ts.get().getConcept(DRAFT_FACT_SNOMED_CONCEPT_CODE).getNid();
		draftFactCurationState = Ts.get().getConcept(DRAFT_FACT_CURATION_STATE).getNid();
		draftFactSetId = Ts.get().getConcept(DRAFT_FACT_SET_ID).getNid();
		draftFactUniqueId = Ts.get().getConcept(DRAFT_FACT_UNIQUE_ID).getNid();
		ndaTypeRootId = Ts.get().getConcept(ndaTypeRoot).getNid();
		splSetId = Ts.get().getConcept(SPL_SET_ID).getNid();
		
		
		//akcdsFactsRelParentNid_ = Ts.get().getConcept(akcdsFactsRelParentUUID_).getConceptNid();
		outputDirectory_ = outputDirectory;
		
		//setId, ndas, drugName, relName, targetName, targetCode, curationState
		delimitedOutput_ = new RF2FileWriter(new String[] {
				"SPL Set ID",   
				"NDAs (TYPE:Value) (comma delimited)",  
				"Drug Name",
				"Assertion Name", 
				"SCT Target Name",  
				"SCT Target Code",  
				"Curation State"},
					new File(outputDirectory_, "SPLFactExport.tsv"), "SPL Delimited Fact Export", false);
	}
	
	public void shutdown() throws IOException
	{
		delimitedOutput_.close();
		ConsoleUtil.println("Viewed " + scannedConcepts_ + " concepts");
		//ConsoleUtil.println("Found " + AKCDSRels_ + " AKCDS facts");
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
		
		Collection<? extends RefexChronicleBI<?>> annotations = c.getAnnotations();
		
		for (RefexChronicleBI<?> annotation : annotations) 
		{
			if (annotation instanceof CidCidCidMember && ((CidCidCidMember)annotation).getRefsetId() == draftFactTripleNid)
			{
				handleDraftFactConcept(c);
				break;
			}
		}
	}
	
	private void handleDraftFactConcept(I_GetConceptData c) throws IOException
	{
		// c is a drug.  Need to go down and get the SPL Set ID info.
		Hashtable<String, ArrayList<String>> setIds = new Hashtable<String, ArrayList<String>>();
		for (I_RelVersioned<?> rel : c.getDestRels())
		{
			ConceptChronicleBI child = Ts.get().getConcept(rel.getOriginNid());
			if (child.toString().equals("SPL Source"))
			{
				String setId = "";
				ArrayList<String> ndas = new ArrayList<String>();
				for (RefexChronicleBI<?> annotation : child.getAnnotations())
				{
					if (annotation instanceof StrMember)
					{
						StrMember annotationStr = (StrMember)annotation;
						if (annotationStr.getRefsetId() == splSetId)
						{
							setId = annotationStr.getStringValue();
						}
						else
						{
							//walk up, to see if it is a child of NDA
							ConceptChronicleBI annotationType = Ts.get().getConcept(annotationStr.getRefsetId());
							for (RelationshipChronicleBI parentRel : annotationType.getRelsOutgoing())
							{
								if (parentRel.getDestinationNid() == ndaTypeRootId)
								{
									ndas.add(annotationType.toString() + ":" + annotationStr.getStringValue());
								}
							}
						}
					}
				}
				setIds.put(setId, ndas);
			}
		}
		
		
		Collection<? extends RefexChronicleBI<?>> annotations = c.getAnnotations();
		
		//And then, process all of the draft facts.
		for (RefexChronicleBI<?> annotation : annotations) 
		{
			if (annotation instanceof CidCidCidMember && ((CidCidCidMember)annotation).getRefsetId() == draftFactTripleNid)
			{
				String targetCode = "";
				String targetName = "";
				String drugName = "";
				String relName = "";
				String curationState = "";
				HashSet<String> foundSetIds = new HashSet<String>();
				
				CidCidCidMember triple = (CidCidCidMember)annotation;
				ConceptChronicleBI drug = Ts.get().getConcept(triple.getC1id());
				ConceptChronicleBI relation = Ts.get().getConcept(triple.getC2id());
				
				drugName = drug.toString();
				relName = relation.toString();
				
				for (RefexChronicleBI<?> tripleAnnotation : triple.getAnnotations())
				{
					
					if (tripleAnnotation instanceof StrMember)
					{
						StrMember tripleAnnotationStr = ((StrMember)tripleAnnotation);
						if (tripleAnnotationStr.getRefsetId() == draftFactSnomedConceptCode)
						{
							targetCode = tripleAnnotationStr.getStringValue();
						}
						else if (tripleAnnotationStr.getRefsetId() == draftFactSnomedConceptNameNid)
						{
							targetName = tripleAnnotationStr.getStringValue();
						}
						else if (tripleAnnotationStr.getRefsetId() == draftFactCurationState)
						{
							curationState = tripleAnnotationStr.getStringValue();
						}
						//There is one of these per unique assertion
						else if (tripleAnnotationStr.getRefsetId() == draftFactUniqueId)
						{
							//A bunch of other stuff is nested under this.. - 
							for (RefexChronicleBI<?> tripleAnnotationStrNestedAnnotation : tripleAnnotationStr.getAnnotations())
							{
								if (tripleAnnotationStrNestedAnnotation instanceof StrMember 
										&& ((StrMember)tripleAnnotationStrNestedAnnotation).getRefsetId() == draftFactSetId)
								{
									foundSetIds.add(((StrMember)tripleAnnotationStrNestedAnnotation).getStringValue());
								}
							}
						}
					}
				}
				//Finally, write out our rows.
				for (String setId : foundSetIds)
				{
					ArrayList<String> ndas = setIds.get(setId);
					if (ndas == null)
					{
						System.err.println("Programmer error - failed to find NDAs!");
					}
					StringBuilder sb = new StringBuilder();
					for (String s : ndas)
					{
						sb.append(s);
						sb.append(",");
					}
					if (sb.length() > 1)
					{
						sb.setLength(sb.length() - 1);
					}
					
					delimitedOutput_.addLine(new String[] {setId, sb.toString(), drugName, relName, targetName, targetCode, curationState});
				}
			}
		}
	}
	
	private UUID getSPLConstantUUID(String name)
	{
		return UUID.nameUUIDFromBytes(("gov.va.spl:metadata:types:" + name).getBytes());
	}
}
