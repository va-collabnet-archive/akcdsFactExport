package gov.va.akcds.export;

import gov.va.akcds.export.util.ConsoleUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.dwfa.ace.api.I_GetConceptData;
import org.dwfa.ace.api.I_RelVersioned;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.concept.ConceptChronicleBI;
import org.ihtsdo.tk.api.relationship.RelationshipAnalogBI;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFWriter;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;

public class RDF
{
	Model model_;
	
	private String snomedURI;
	private String splRelationURI;
	private String ndfrtURI;
	
	String snomedPrefix = "snomed";
	String ndfrtPrefix = "ndfrt";
	String splPrefix = "spl";
	String labelPrefix = "label";
	
	Property label;

	public RDF() throws URISyntaxException
	{
		model_ = ModelFactory.createDefaultModel();
		snomedURI = new URI("http://aceworkspace.net/content/snomed/2010.07.31-build1-sct-en-vtmvmp-non-human-2-version#").toString();
		splRelationURI = new URI("http://aceworkspace.net/content/spl/20110919-BW-batch-1-2-3-4-build-1#").toString();
		ndfrtURI = new URI("http://aceworkspace.net/content/ndf-rt/2011.07.05.11AA-build-1#").toString();

		model_.setNsPrefix(splPrefix, splRelationURI);
		model_.setNsPrefix(snomedPrefix, snomedURI);
		model_.setNsPrefix(ndfrtPrefix, ndfrtURI);
		
		//Jena seems to be broken, and doesn't properly handle prefixes with the value after the prefix starts with a number.
		//So I added the UUID- stuff.  Obviously something I don't understand.
		
	}

	public void addConcept(I_GetConceptData sourceConcept, I_RelVersioned<RelationshipAnalogBI<?>> rel) throws IOException
	{
		ConceptChronicleBI relConcept = Ts.get().getConcept(rel.getTypeNid());
		ConceptChronicleBI targetConcept = Ts.get().getConcept(rel.getDestinationNid());
		
		Property relProperty = model_.createProperty(splRelationURI + "UUID-" +  relConcept.getPrimUuid().toString());
		if (!relProperty.hasProperty(com.hp.hpl.jena.vocabulary.RDFS.label))
		{
			relProperty.addProperty(com.hp.hpl.jena.vocabulary.RDFS.label, relConcept.toString());
		}
		
		Resource source = model_.getResource(ndfrtURI + "UUID-" +  sourceConcept.getPrimUuid().toString());
		if (!source.hasProperty(com.hp.hpl.jena.vocabulary.RDFS.label))
		{
			source.addProperty(com.hp.hpl.jena.vocabulary.RDFS.label, sourceConcept.toString());
		}
		
		Resource target = model_.getResource(snomedURI + "UUID-" +  targetConcept.getPrimUuid().toString());
		if (!target.hasProperty(com.hp.hpl.jena.vocabulary.RDFS.label))
		{
			target.addProperty(com.hp.hpl.jena.vocabulary.RDFS.label, targetConcept.toString());
		}
		
		Statement s = model_.createStatement(source, relProperty, target);
		model_.add(s);

	}

	public void export(File outputDirectory) throws IOException
	{
		RDFWriter rdfWriter = model_.getWriter("N3-TRIPLE");

		File file = new File(outputDirectory, "export.n3");
		ConsoleUtil.println("See " + file.getAbsolutePath() + " for RDF N3 Output");
		OutputStream out = new FileOutputStream(file);
		rdfWriter.write(model_, out, null);
		out.close();
		
		rdfWriter = model_.getWriter("RDF/XML-ABBREV");

		file = new File(outputDirectory, "export.rdf");
		ConsoleUtil.println("See " + file.getAbsolutePath() + " for RDF Output");
		out = new FileOutputStream(file);
		rdfWriter.write(model_, out, null);
		out.close();
		
	}
}
