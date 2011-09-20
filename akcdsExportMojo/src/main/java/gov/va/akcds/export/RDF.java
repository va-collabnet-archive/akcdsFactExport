package gov.va.akcds.export;

import gov.va.akcds.export.util.ConsoleUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

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

	public RDF()
	{
		model_ = ModelFactory.createDefaultModel();
	}

	public void addConcept(I_GetConceptData sourceConcept, I_RelVersioned<RelationshipAnalogBI<?>> rel) throws IOException
	{
		ConceptChronicleBI relConcept = Ts.get().getConcept(rel.getTypeNid());
		ConceptChronicleBI targetConcept = Ts.get().getConcept(rel.getDestinationNid());
		
		Property relProperty = model_.createProperty(relConcept.getPrimUuid().toString());
		if (!relProperty.hasProperty(com.hp.hpl.jena.vocabulary.RDFS.label))
		{
			relProperty.addProperty(com.hp.hpl.jena.vocabulary.RDFS.label, relConcept.toString());
		}
		
		Resource source = model_.getResource(sourceConcept.getPrimUuid().toString());
		if (!source.hasProperty(com.hp.hpl.jena.vocabulary.RDFS.label))
		{
			source.addProperty(com.hp.hpl.jena.vocabulary.RDFS.label, sourceConcept.toString());
		}
		
		Resource target = model_.getResource(targetConcept.getPrimUuid().toString());
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

		File file = new File(outputDirectory, "export.rdf");
		ConsoleUtil.println("See " + file.getAbsolutePath() + " for RDF Output");
		OutputStream out = new FileOutputStream(file);
		rdfWriter.write(model_, out, "http://example.org/");
		out.close();
	}
}
