package FormatConverterLib;

import bioc.BioCAnnotation;
import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.BioCPassage;
import bioc.BioCRelation;
import bioc.BioCNode;

import bioc.io.BioCDocumentWriter;
import bioc.io.BioCFactory;
import bioc.io.woodstox.ConnectorWoodstox;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.ZoneId;

import javax.xml.stream.XMLStreamException;

import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.sl.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

import com.google.gson.Gson;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

/**
 * 
 * using http://pdfbox.apache.org/download.cgi#20x for pdf2text
 *
 */

public class FormatConverter 
{
	/*
	 * Contexts in BioC file
	 */
	public ArrayList<String> PMIDs=new ArrayList<String>(); // Type: PMIDs
	public ArrayList<ArrayList<String>> PassageNames = new ArrayList(); // PassageName
	public ArrayList<ArrayList<Integer>> PassageOffsets = new ArrayList(); // PassageOffset
	public ArrayList<ArrayList<String>> PassageContexts = new ArrayList(); // PassageContext
	public ArrayList<ArrayList<ArrayList<String>>> Annotations = new ArrayList(); // Annotation - GNormPlus
	
	public static String BioCFormatCheck(String InputFile) throws IOException
	{
		if(InputFile.endsWith(".xlsx"))
		{
			return "Excelx";
		}
		else if(InputFile.endsWith(".xls"))
		{
			return "Excel";
		}
		else if(InputFile.endsWith(".docx"))
		{
			return "Wordx";
		}
		else if(InputFile.endsWith(".doc"))
		{
			return "Word";
		}
		else if(InputFile.endsWith(".tar.gz"))
		{
			return "tar.gz";
		}
		else
		{
			File file = new File(InputFile);
			PDFParser pdfparser = new PDFParser(new RandomAccessFile(file,"r"));
			try
			{
				pdfparser.parse();
				return "PDF";
			}
			catch (IOException notpdf)
			{
				ConnectorWoodstox connector = new ConnectorWoodstox();
				BioCCollection collection = new BioCCollection();
				try
				{
					collection = connector.startRead(new InputStreamReader(new FileInputStream(InputFile), "UTF-8"));
				}
				catch (UnsupportedEncodingException | FileNotFoundException | XMLStreamException e) //if not BioC
				{
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(InputFile), "UTF-8"));
					String line="";
					String status="";
					String Pmid = "";
					boolean tiabs=false;
					Pattern patt = Pattern.compile("^([^\\|\\t]+)\\|([^\\|\\t]+)\\|([^\\|\\t]*)$");
					while ((line = br.readLine()) != null)  
					{
						Matcher mat = patt.matcher(line);
						if(mat.find()) //Title|Abstract
			        	{
							if(Pmid.equals(""))
							{
								Pmid = mat.group(1);
							}
							else if(!Pmid.equals(mat.group(1)))
							{
								return "[Error of PubTator format]: "+InputFile+" - A blank is needed between "+Pmid+" and "+mat.group(1)+".";
							}
							status = "tiabs";
							tiabs = true;
			        	}
						else if (line.contains("\t")) //Annotation
			        	{
			        	}
						else if(line.length()==0) //Processing
						{
							if(status.equals(""))
							{
								if(Pmid.equals(""))
								{
									return "[Error 1.0]: "+InputFile+" - It's neither BioC nor PubTator format.";
								}
								else
								{
									return "[Error of PubTator format]: "+InputFile+" - A redundant blank is after "+Pmid+".";
								}
							}
							Pmid="";
							status="";
						}
					}
					br.close();
					if(tiabs == false)
					{
						return "[Error 1.1]: "+InputFile+" - It's neither BioC nor PubTator format.";
					}
					
					if(status.equals(""))
					{
						return "PubTator";
					}
					else
					{
						return "[Error of PubTator format]: "+InputFile+" - The last column missed a blank.";
					}
				}
				return "BioC";
			}
		}
	}
	public static void PubTator2BioC(String input,String output) throws IOException, XMLStreamException
	{
		String parser = BioCFactory.WOODSTOX;
		BioCFactory factory = BioCFactory.newFactory(parser);
		BioCDocumentWriter BioCOutputFormat = factory.createBioCDocumentWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		BioCCollection biocCollection = new BioCCollection();
		
		//time
		ZoneId zonedId = ZoneId.of( "America/Montreal" );
		LocalDate today = LocalDate.now( zonedId );
		biocCollection.setDate(today.toString());
		
		biocCollection.setKey("BioC.key");//key
		biocCollection.setSource("PubTator");//source
		
		BioCOutputFormat.writeCollectionInfo(biocCollection);
		BufferedReader inputfile = new BufferedReader(new InputStreamReader(new FileInputStream(input), "UTF-8"));
		ArrayList<String> ParagraphType=new ArrayList<String>(); // Type: Title|Abstract
		ArrayList<String> ParagraphContent = new ArrayList<String>(); // Text
		ArrayList<String> annotations = new ArrayList<String>(); // Annotation
		ArrayList<String> relations = new ArrayList<String>(); // relation
		String line;
		String Pmid="";
		while ((line = inputfile.readLine()) != null)  
		{
			if(line.contains("|") && !line.contains("\t")) //Title|Abstract
        	{
				String str[]=line.split("\\|",-1);
				Pmid=str[0];
				if(str[1].equals("t"))
				{
					str[1]="title";
				}
				if(str[1].equals("a"))
				{
					str[1]="abstract";
				}
				ParagraphType.add(str[1]);
				if(str.length==3)
				{
					ParagraphContent.add(str[2]);
				}
				else
				{
					ParagraphContent.add("");
				}
        	}
			else if (line.contains("\t")) //Annotation
        	{
				String anno[]=line.split("\t",-1);
				if(anno.length==6 && anno[1].matches("[0-9]+"))
				{
					annotations.add(anno[1]+"\t"+anno[2]+"\t"+anno[3]+"\t"+anno[4]+"\t"+anno[5]);
				}
				else if(anno.length==5 && anno[1].matches("[0-9]+"))
				{
					annotations.add(anno[1]+"\t"+anno[2]+"\t"+anno[3]+"\t"+anno[4]);
				}
				else if(anno.length>=4)
				{
					relations.add(anno[1]+"\t"+anno[2]+"\t"+anno[3]);
				}
        	}
			else if(line.length()==0) //Processing
			{
				BioCDocument biocDocument = new BioCDocument();
				biocDocument.setID(Pmid);
				int startoffset=0;
				for(int i=0;i<ParagraphType.size();i++)
				{
					BioCPassage biocPassage = new BioCPassage();
					Map<String, String> Infons = new HashMap<String, String>();
					Infons.put("type", ParagraphType.get(i));
					biocPassage.setInfons(Infons);
					biocPassage.setText(ParagraphContent.get(i));
					biocPassage.setOffset(startoffset);
					startoffset=startoffset+ParagraphContent.get(i).length()+1;
					for(int j=0;j<annotations.size();j++)
					{
						String anno[]=annotations.get(j).split("\t");
						if((Integer.parseInt(anno[0])<startoffset || Integer.parseInt(anno[0])==0) && Integer.parseInt(anno[0])>=startoffset-(ParagraphContent.get(i).length()+1))
						{
							BioCAnnotation biocAnnotation = new BioCAnnotation();
							Map<String, String> AnnoInfons = new HashMap<String, String>();
							if(anno.length==5)
							{
								AnnoInfons.put("identifier", anno[4]);
							}
							AnnoInfons.put("type", anno[3]);
							biocAnnotation.setInfons(AnnoInfons);
							BioCLocation location = new BioCLocation();
							location.setOffset(Integer.parseInt(anno[0]));
							location.setLength(Integer.parseInt(anno[1])-Integer.parseInt(anno[0]));
							biocAnnotation.setLocation(location);
							biocAnnotation.setText(anno[2]);
							biocAnnotation.setID(""+j);
							biocPassage.addAnnotation(biocAnnotation);
						}
					}
					biocDocument.addPassage(biocPassage);
				}
				for(int j=0;j<relations.size();j++)
				{
					String rel[]=relations.get(j).split("\t");
					BioCRelation biocrelation = new BioCRelation();
					Map<String, String> relationtype = new HashMap<String, String>();
					relationtype.put("relation", rel[0]);
					relationtype.put("Gene1", rel[1]);
					relationtype.put("Gene2", rel[2]);
					biocrelation.setInfons(relationtype);
					biocrelation.setID("R"+j);
					biocDocument.addRelation(biocrelation);
				}
				biocCollection.addDocument(biocDocument);
				ParagraphType.clear();
				ParagraphContent.clear();
				annotations.clear();
				relations.clear();
				BioCOutputFormat.writeDocument(biocDocument);
			}
		}
		BioCOutputFormat.close();
		inputfile.close();
	}
	public static void BioC2PubTator(String input,String output) throws IOException, XMLStreamException
	{
		HashMap<String, String> pmidlist = new HashMap<String, String>(); // check if appear duplicate pmids
		boolean duplicate = false;
		BufferedWriter PubTatorOutputFormat = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		ConnectorWoodstox connector = new ConnectorWoodstox();
		BioCCollection collection = new BioCCollection();
		collection = connector.startRead(new InputStreamReader(new FileInputStream(input), "UTF-8"));
		while (connector.hasNext()) 
		{
			BioCDocument document = connector.next();
			String PMID = document.getID();
			if(pmidlist.containsKey(PMID)){System.out.println("\n[Warning]: duplicate pmid-"+PMID);duplicate = true;}
			else{pmidlist.put(PMID,"");}
			String Anno="";
			int count_passage=1;
			for (BioCPassage passage : document.getPassages()) 
			{
				if((!passage.getInfons().isEmpty()) && passage.getInfon("type").equals("title"))
				{
					PubTatorOutputFormat.write(PMID+"|t|"+passage.getText()+"\n");
				}
				else if((!passage.getInfons().isEmpty()) && passage.getInfon("type").equals("abstract"))
				{
					PubTatorOutputFormat.write(PMID+"|a|"+passage.getText()+"\n");
				}
				else if((!passage.getInfons().isEmpty()))
				{
					PubTatorOutputFormat.write(PMID+"|"+passage.getInfon("type")+"|"+passage.getText()+"\n");
				}
				else
				{
					PubTatorOutputFormat.write(PMID+"|Passage_"+count_passage+"|"+passage.getText()+"\n");
				}
				
				for (BioCAnnotation annotation : passage.getAnnotations()) 
				{
					String Annotype = annotation.getInfon("type");
					String Annoid="";
					Map<String,String> Infons = annotation.getInfons();
					for(String InfonType : Infons.keySet())
					{
						if(!InfonType.equals("type"))
						{
							if(Annoid.equals(""))
							{
								Annoid=Infons.get(InfonType);
							}
							else
							{
								Annoid=Annoid+"|"+Infons.get(InfonType);
							}
						}
					}
					int start = annotation.getLocations().get(0).getOffset();
					int last = start + annotation.getLocations().get(0).getLength();
					String AnnoMention=annotation.getText();
					Anno=Anno+PMID+"\t"+start+"\t"+last+"\t"+AnnoMention+"\t"+Annotype+"\t"+Annoid+"\n";
				}
				count_passage++;
			}
			PubTatorOutputFormat.write(Anno);
			
			//relation
			String Rel="";
			for (BioCRelation biocrelation : document.getRelations()) 
			{
				Rel=Rel+PMID+"\t"+biocrelation.getInfon("relation")+"\t"+biocrelation.getInfon("Gene1")+"\t"+biocrelation.getInfon("Gene2")+"\n";
			}
			PubTatorOutputFormat.write(Rel+"\n");
		}
		PubTatorOutputFormat.close();
		if(duplicate == true){System.exit(0);}
	}
	public static void BioC2SciLite(String input,String output) throws IOException, XMLStreamException
	{
		BufferedWriter outputfile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		HashMap<String, String> pmidlist = new HashMap<String, String>(); // check if appear duplicate pmids
		boolean duplicate = false;
		ConnectorWoodstox connector = new ConnectorWoodstox();
		BioCCollection collection = new BioCCollection();
		collection = connector.startRead(new InputStreamReader(new FileInputStream(input), "UTF-8"));
		while (connector.hasNext()) 
		{
			BioCDocument document = connector.next();
			String PMID = document.getID();
			if(pmidlist.containsKey(PMID)){System.out.println("\nWarning: duplicate pmid-"+PMID);duplicate = true;}
			else{pmidlist.put(PMID,"");}
			String Anno="";
			int count_passage=0;
			for (BioCPassage passage : document.getPassages()) 
			{
				count_passage++;
				
				int count_ann=0;
				/* Annotation */
				for (BioCAnnotation annotation : passage.getAnnotations()) 
				{
					count_ann++;
					String Annotype = annotation.getInfon("type");
					String Annoid="";
					Map<String,String> Infons = annotation.getInfons();
					for(String InfonType : Infons.keySet()) // check all Infontype
					{
						if(!InfonType.equals("type"))
						{
							if(Annoid.equals(""))
							{
								Annoid=Infons.get(InfonType);
							}
							else
							{
								Annoid=Annoid+"|"+Infons.get(InfonType);
							}
						}
					}
					Annoid=Annoid.replace("RS#:", "");
					int start = annotation.getLocations().get(0).getOffset()-passage.getOffset();
					int last = start + annotation.getLocations().get(0).getLength();
					String AnnoMention=annotation.getText();
					String prefix="";
					String postfix="";
					
					if(start>20)
					{
						prefix=passage.getText().substring(start-20,start);
					}
					else
					{
						prefix=passage.getText().substring(0,start);
					}
					
					if(passage.getText().length()-last>20)
					{
						postfix=passage.getText().substring(last,last+20);
					}
					else
					{
						postfix=passage.getText().substring(last,passage.getText().length());
					}
					
					HashMap <String,String> jo = new HashMap <String,String>();
					jo.put("ann", "http://rdf.ebi.ac.uk/resource/europepmc/annotations/PMC"+PMID+"#"+count_passage+"-"+count_ann);
					jo.put("position",count_passage+"."+count_ann);
					if(Annoid.matches("[0-9]+"))
					{
						jo.put("tag", "http://identifiers.org/dbsnp/rs"+Annoid);
					}
					jo.put("prefix", prefix);
					jo.put("exact", AnnoMention);
					jo.put("postfix", postfix);
					jo.put("pmcid", "PMC"+PMID);
					Gson gson = new Gson(); 
					String json = gson.toJson(jo); 
					outputfile.write(json+"\n");
				}
			}
		}
		if(duplicate == true){System.exit(0);}
		outputfile.close();
	}
	public static void FreeText2PubTator(String input,String output) throws IOException
	{
		BufferedReader inputfile = new BufferedReader(new InputStreamReader(new FileInputStream(input), "UTF-8"));
		BufferedWriter outputfile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		
		String line;
		int count_line=0;
		while ((line = inputfile.readLine()) != null)  
		{
			line=line.replaceAll("^[\t ]+", "");
			if(!line.equals(""))
			{
				if(count_line==0)
				{
					outputfile.write(input+"|t|"+line+"\n");
				}
				else if(count_line==1)
				{
					outputfile.write(input+"|a|"+line);
				}
				else
				{
					outputfile.write(" "+line);
				}
				count_line++;
			}
		}
		outputfile.write("\n");
		inputfile.close();
		outputfile.close();
	}
	public static void FreeText2BioC(String input,String output) throws IOException, XMLStreamException
	{
		String parser = BioCFactory.WOODSTOX;
		BioCFactory factory = BioCFactory.newFactory(parser);
		BioCDocumentWriter BioCOutputFormat = factory.createBioCDocumentWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		BioCCollection biocCollection = new BioCCollection();
		
		//time
		ZoneId zonedId = ZoneId.of( "America/Montreal" );
		LocalDate today = LocalDate.now( zonedId );
		biocCollection.setDate(today.toString());
		
		biocCollection.setKey("BioC.key");//key
		biocCollection.setSource("BioC");//source
		
		BioCOutputFormat.writeCollectionInfo(biocCollection);
		BufferedReader inputfile = new BufferedReader(new InputStreamReader(new FileInputStream(input), "UTF-8"));
		String line;
		BioCDocument biocDocument = new BioCDocument();
		input=input.replaceAll("[^0-9]","");
		biocDocument.setID(input);
		int startoffset=0;
		int count_line=0;
		while ((line = inputfile.readLine()) != null)  
		{
			line=line.replaceAll("^[\t \\W\\-\\_]+", "");
			if(!line.equals(""))
			{
				count_line++;
				BioCPassage biocPassage = new BioCPassage();
				Map<String, String> Infons = new HashMap<String, String>();
				Infons.put("type", "Line_"+count_line);
				biocPassage.setInfons(Infons);
				biocPassage.setText(line);
				biocPassage.setOffset(startoffset);
				startoffset=startoffset+line.length()+1;
				biocDocument.addPassage(biocPassage);
			}
		}
		biocCollection.addDocument(biocDocument);
		BioCOutputFormat.writeDocument(biocDocument);
	
		BioCOutputFormat.close();
		inputfile.close();
	}
	public static void PubTator2HTML(String input,String output) throws IOException, XMLStreamException
	{
		ArrayList<String> color_arr = new ArrayList<String>();
		int color_arr_count=0;
		color_arr.add("255,153,0");color_arr.add("102,204,0");color_arr.add("200,64,240");color_arr.add("0,208,255");color_arr.add("130,210,170");color_arr.add("250,150,150");color_arr.add("150,150,250");color_arr.add("150,250,250");color_arr.add("250,150,250");color_arr.add("180,80,180");color_arr.add("250,220,180");color_arr.add("180,180,80");color_arr.add("230,230,230");color_arr.add("230,230,130");color_arr.add("230,230,30");color_arr.add("230,180,230");color_arr.add("230,180,130");color_arr.add("230,180,30");color_arr.add("230,130,230");color_arr.add("230,130,130");color_arr.add("230,130,30");color_arr.add("230,80,230");color_arr.add("230,80,130");color_arr.add("230,80,30");color_arr.add("230,30,230");color_arr.add("230,30,130");color_arr.add("230,30,30");color_arr.add("180,230,230");color_arr.add("180,230,130");color_arr.add("180,230,30");color_arr.add("180,180,230");color_arr.add("180,180,130");color_arr.add("180,180,30");color_arr.add("180,130,224");color_arr.add("180,130,130");color_arr.add("180,130,30");color_arr.add("180,80,230");color_arr.add("180,80,130");color_arr.add("180,80,30");color_arr.add("180,30,230");color_arr.add("180,30,130");color_arr.add("180,30,30");color_arr.add("130,230,230");color_arr.add("130,230,130");color_arr.add("130,230,30");color_arr.add("130,180,230");color_arr.add("130,180,130");color_arr.add("130,180,30");color_arr.add("130,130,230");color_arr.add("130,130,130");color_arr.add("130,130,30");color_arr.add("130,80,230");color_arr.add("130,80,130");color_arr.add("130,80,30");color_arr.add("130,30,230");color_arr.add("130,30,130");color_arr.add("130,30,30");color_arr.add("80,230,230");color_arr.add("80,230,130");color_arr.add("80,230,30");color_arr.add("80,180,230");color_arr.add("80,180,130");color_arr.add("80,180,30");color_arr.add("80,130,230");color_arr.add("80,130,130");color_arr.add("80,130,30");color_arr.add("80,80,230");color_arr.add("80,80,130");color_arr.add("80,80,30");color_arr.add("80,30,230");color_arr.add("80,30,130");color_arr.add("80,30,30");
		HashMap<String,String> color_hash = new HashMap<String,String> ();
		
		BufferedReader inputfile = new BufferedReader(new InputStreamReader(new FileInputStream(input), "UTF-8"));
		HashMap<String, String> annotation_hash = new HashMap<String, String>();
		HashMap<String, Integer> annotation_count_hash = new HashMap<String, Integer>();
		ArrayList<String> ParagraphType=new ArrayList<String>(); // Type: Title|Abstract
		ArrayList<String> ParagraphContent = new ArrayList<String>(); // Text
		ArrayList<String> annotation_arr = new ArrayList<String>(); // Annotation
		HashMap<Integer, String> annotation_mention_hash = new HashMap<Integer, String>();
		String line;
		String Pmid="";
		String output_STR="";
		int count_anno=0;
		while ((line = inputfile.readLine()) != null)  
		{
			if(line.contains("|") && !line.contains("\t")) //Title|Abstract
        	{
				String str[]=line.split("\\|",-1);
				Pmid=str[0];
				if(str[1].equals("t"))
				{
					str[1]="title";
				}
				if(str[1].equals("a"))
				{
					str[1]="abstract";
				}
				ParagraphType.add(str[1]);
				if(str.length==3)
				{
					ParagraphContent.add(str[2]);
				}
				else
				{
					ParagraphContent.add("");
				}
        	}
			else if (line.contains("\t")) //Annotation
        	{
				String anno[]=line.split("\t");
				String start=anno[1];
				String last=anno[2];
				String AnnoMention=anno[3];
				String Annotype=anno[4];
				
				if(anno.length==6)
				{
					String Annoid=anno[5];
					annotation_mention_hash.put(Integer.parseInt(start), start+"\t"+last+"\t"+AnnoMention+"\t"+Annotype+"\t"+Annoid);
				}
				else if(anno.length==5)
				{
					annotation_mention_hash.put(Integer.parseInt(start), start+"\t"+last+"\t"+AnnoMention+"\t"+Annotype);
				}
				count_anno++;
        	}
			else if(line.length()==0) //Processing
			{
				String Paragraphs="";
				for(int i=0;i<ParagraphContent.size();i++)
				{
					Paragraphs=Paragraphs+ParagraphContent.get(i)+" ";
				}
				
				while(count_anno>0)
				{
					int max_start=0;
					for(Integer start : annotation_mention_hash.keySet())
					{
						if(start>max_start)
						{
							max_start=start;
						}
					}
					annotation_arr.add(annotation_mention_hash.get(max_start));
					annotation_mention_hash.remove(max_start);
					count_anno--;
				}
				for(int x=0;x<annotation_arr.size();x++)
				{
					String str[]=annotation_arr.get(x).split("\\t");
					int start = Integer.parseInt(str[0]);
					int last = Integer.parseInt(str[1]);
					String mention=str[2];
					String type=str[3];
					String id="";
					if(str.length==5)
					{
						id=str[4];
					}
					annotation_hash.put(type+"\t"+id,mention);
					if(!annotation_count_hash.containsKey(type+"\t"+id))
					{
						annotation_count_hash.put(type+"\t"+id,1);
					}
					else
					{
						annotation_count_hash.put(type+"\t"+id,annotation_count_hash.get(type+"\t"+id)+1);
					}
					String pre=Paragraphs.substring(0, start);
					String post=Paragraphs.substring(last, Paragraphs.length());
					if(!color_hash.containsKey(type))
					{
						color_hash.put(type, color_arr.get(color_arr_count));
						color_arr_count++;
					}
					Paragraphs=pre+"<font style=\"background-color: rgb("+color_hash.get(type)+")\" title='"+id+"'>"+mention+"</font>"+post;
				}
				output_STR=output_STR+"PMID:"+Pmid+"<BR />"+Paragraphs+"<BR /><BR />\n";
				
				ParagraphType.clear();
				ParagraphContent.clear();
				annotation_arr.clear();
				annotation_mention_hash.clear();
				count_anno=0;
			}
		}
		inputfile.close();
		
		BufferedWriter HTMLOutputFormat = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		HTMLOutputFormat.write("<!DOCTYPE html>\n<html><head>\n<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n<title>BioC Documents</title>\n</head><body>");
		HTMLOutputFormat.write("<table border=1><tr><td>Type</td><td>concepts (identifiers) - mentioned frequency</td></tr>");
		for(String type: color_hash.keySet())
		{
			HTMLOutputFormat.write("<tr style=\"background-color: rgb("+color_hash.get(type)+")\">");
			HTMLOutputFormat.write("<td>"+type+"</td>");
			HTMLOutputFormat.write("<td>");
			
			for(String typeid: annotation_hash.keySet())
			{
				String type_id[]=typeid.split("\\t");
				if(type_id[0].equals(type))
				{
					if(type_id[0].equals(type_id[1]))
					{
						HTMLOutputFormat.write(annotation_hash.get(typeid)+" - "+annotation_count_hash.get(typeid)+"<BR />");
					}
					else
					{
						HTMLOutputFormat.write(annotation_hash.get(typeid)+" ("+type_id[1]+") - "+annotation_count_hash.get(typeid)+"<BR />");
					}
				}
			}
			HTMLOutputFormat.write("</td>");
			HTMLOutputFormat.write("</tr>");
		}
		HTMLOutputFormat.write("</table><BR />");
		HTMLOutputFormat.write(output_STR);
		HTMLOutputFormat.write("</body></html>");
		HTMLOutputFormat.close();
		
	}
	public static void BioC2HTML(String input,String output) throws IOException, XMLStreamException
	{
		ArrayList<String> color_arr = new ArrayList<String>();
		int color_arr_count=0;
		color_arr.add("255,153,0");color_arr.add("102,204,0");color_arr.add("200,64,240");color_arr.add("0,208,255");color_arr.add("130,210,170");color_arr.add("250,150,150");color_arr.add("150,150,250");color_arr.add("150,250,250");color_arr.add("250,150,250");color_arr.add("180,80,180");color_arr.add("250,220,180");color_arr.add("180,180,80");color_arr.add("230,230,230");color_arr.add("230,230,130");color_arr.add("230,230,30");color_arr.add("230,180,230");color_arr.add("230,180,130");color_arr.add("230,180,30");color_arr.add("230,130,230");color_arr.add("230,130,130");color_arr.add("230,130,30");color_arr.add("230,80,230");color_arr.add("230,80,130");color_arr.add("230,80,30");color_arr.add("230,30,230");color_arr.add("230,30,130");color_arr.add("230,30,30");color_arr.add("180,230,230");color_arr.add("180,230,130");color_arr.add("180,230,30");color_arr.add("180,180,230");color_arr.add("180,180,130");color_arr.add("180,180,30");color_arr.add("180,130,224");color_arr.add("180,130,130");color_arr.add("180,130,30");color_arr.add("180,80,230");color_arr.add("180,80,130");color_arr.add("180,80,30");color_arr.add("180,30,230");color_arr.add("180,30,130");color_arr.add("180,30,30");color_arr.add("130,230,230");color_arr.add("130,230,130");color_arr.add("130,230,30");color_arr.add("130,180,230");color_arr.add("130,180,130");color_arr.add("130,180,30");color_arr.add("130,130,230");color_arr.add("130,130,130");color_arr.add("130,130,30");color_arr.add("130,80,230");color_arr.add("130,80,130");color_arr.add("130,80,30");color_arr.add("130,30,230");color_arr.add("130,30,130");color_arr.add("130,30,30");color_arr.add("80,230,230");color_arr.add("80,230,130");color_arr.add("80,230,30");color_arr.add("80,180,230");color_arr.add("80,180,130");color_arr.add("80,180,30");color_arr.add("80,130,230");color_arr.add("80,130,130");color_arr.add("80,130,30");color_arr.add("80,80,230");color_arr.add("80,80,130");color_arr.add("80,80,30");color_arr.add("80,30,230");color_arr.add("80,30,130");color_arr.add("80,30,30");
		HashMap<String,String> color_hash = new HashMap<String,String> ();
		
		HashMap<String, String> pmidlist = new HashMap<String, String>(); // check if appear duplicate pmids
		boolean duplicate = false;
		ConnectorWoodstox connector = new ConnectorWoodstox();
		BioCCollection collection = new BioCCollection();
		collection = connector.startRead(new InputStreamReader(new FileInputStream(input), "UTF-8"));
		String output_STR="";
		HashMap<String, String> annotation_hash = new HashMap<String, String>();
		HashMap<String, Integer> annotation_count_hash = new HashMap<String, Integer>();
		while (connector.hasNext()) 
		{
			BioCDocument document = connector.next();
			String PMID = document.getID();
			if(pmidlist.containsKey(PMID)){System.out.println("\n[Warning]: duplicate pmid-"+PMID);duplicate = true;}
			else{pmidlist.put(PMID,"");}
			for (BioCPassage passage : document.getPassages()) 
			{
				String passage_text=passage.getText();
				HashMap<Integer, String> annotation_mention_hash = new HashMap<Integer, String>();
				ArrayList<String> annotation_arr = new ArrayList<String>();
				int count_anno=0;
				for (BioCAnnotation annotation : passage.getAnnotations()) 
				{
					String Annotype = annotation.getInfon("type");
					int start = annotation.getLocations().get(0).getOffset();
					int last = start + annotation.getLocations().get(0).getLength();
					String AnnoMention=annotation.getText();
					Map<String,String> Infons = annotation.getInfons();
					String Annoid = "";
					for(String InfonType : Infons.keySet())
					{
						if(!InfonType.equals("type"))
						{
							if(Annoid.equals(""))
							{
								Annoid=Infons.get(InfonType);
							}
							else
							{
								Annoid=Annoid+"|"+Infons.get(InfonType);
							}
						}
					}
					if(Annoid.equals(""))
					{
						annotation_mention_hash.put(start, start+"\t"+last+"\t"+AnnoMention+"\t"+Annotype);
					}
					else
					{
						annotation_mention_hash.put(start, start+"\t"+last+"\t"+AnnoMention+"\t"+Annotype+"\t"+Annoid);
					}
						
					count_anno++;
				}
				while(count_anno>0)
				{
					int max_start=0;
					for(Integer start : annotation_mention_hash.keySet())
					{
						if(start>max_start)
						{
							max_start=start;
						}
					}
					annotation_arr.add(annotation_mention_hash.get(max_start));
					annotation_mention_hash.remove(max_start);
					count_anno--;
				}
				for(int x=0;x<annotation_arr.size();x++)
				{
					String str[]=annotation_arr.get(x).split("\\t");
					int start = Integer.parseInt(str[0])-passage.getOffset();
					int last = Integer.parseInt(str[1])-passage.getOffset();
					String mention=str[2];
					String type=str[3];
					String id="";
					if(str.length==5)
					{
						id=str[4];
					}
					annotation_hash.put(type+"\t"+id,mention);
					if(!annotation_count_hash.containsKey(type+"\t"+id))
					{
						annotation_count_hash.put(type+"\t"+id,1);
					}
					else
					{
						annotation_count_hash.put(type+"\t"+id,annotation_count_hash.get(type+"\t"+id)+1);
					}
					String pre=passage_text.substring(0, start);
					String post=passage_text.substring(last, passage_text.length());
					if(!color_hash.containsKey(type))
					{
						color_hash.put(type, color_arr.get(color_arr_count));
						color_arr_count++;
					}
					passage_text=pre+"<font style=\"background-color: rgb("+color_hash.get(type)+")\" title='"+id+"'>"+mention+"</font>"+post;
				}
				output_STR=output_STR+passage_text+"<BR /><BR />\n";
			}
		}
		if(duplicate == true){System.exit(0);}
		
		BufferedWriter HTMLOutputFormat = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		HTMLOutputFormat.write("<!DOCTYPE html>\n<html><head>\n<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n<title>BioC Documents</title>\n</head><body>");
		HTMLOutputFormat.write("<table border=1><tr><td>Type</td><td>concepts (identifiers) - mentioned frequency</td></tr>");
		for(String type: color_hash.keySet())
		{
			HTMLOutputFormat.write("<tr style=\"background-color: rgb("+color_hash.get(type)+")\">");
			HTMLOutputFormat.write("<td>"+type+"</td>");
			HTMLOutputFormat.write("<td>");
			
			for(String typeid: annotation_hash.keySet())
			{
				String type_id[]=typeid.split("\\t");
				if(type_id[0].equals(type))
				{
					if(type_id[0].equals(type_id[1]))
					{
						HTMLOutputFormat.write(annotation_hash.get(typeid)+" - "+annotation_count_hash.get(typeid)+"<BR />");
					}
					else
					{
						HTMLOutputFormat.write(annotation_hash.get(typeid)+" ("+type_id[1]+") - "+annotation_count_hash.get(typeid)+"<BR />");
					}
				}
			}
			HTMLOutputFormat.write("</td>");
			HTMLOutputFormat.write("</tr>");
		}
		HTMLOutputFormat.write("</table><BR />");
		HTMLOutputFormat.write(output_STR);
		HTMLOutputFormat.write("</body></html>");
		HTMLOutputFormat.close();
	}
	public static void BioC2HTML_AlzPED(String input,String output) throws IOException, XMLStreamException //Output
	{
		ArrayList<String> color_arr = new ArrayList<String>();
		int color_arr_count=0;
		HashMap<String,String> color_hash = new HashMap<String,String> ();
		HashMap<String,String> color_used_hash = new HashMap<String,String> ();
		color_hash.put("ADME","255,153,0");
		color_hash.put("Behavior","102,204,0");
		color_hash.put("Biochemistry","200,64,240");
		color_hash.put("Biomarker","0,208,255");
		color_hash.put("Cell Biology","130,210,170");
		color_hash.put("Electron Microscopy","250,150,150");
		color_hash.put("Electrophysiology","150,150,250");
		color_hash.put("Histopathology","150,250,250");
		color_hash.put("Imaging","250,150,250");
		color_hash.put("Immunochemistry","180,80,180");
		color_hash.put("Immunology","250,220,180");
		color_hash.put("Microscopy","180,180,80");
		color_hash.put("Model","230,230,230");
		color_hash.put("Motor Function","230,230,130");
		color_hash.put("Pharmacodynamicis","230,230,30");
		color_hash.put("Pharmacokinetics","230,180,230");
		color_hash.put("Pharmacology","230,180,130");
		color_hash.put("Spectroscopy","230,180,30");
		color_hash.put("Strain","230,130,230");
		color_hash.put("Target","230,130,130");
		color_hash.put("Toxicology","230,130,30");
		
		HashMap<String, String> pmidlist = new HashMap<String, String>(); // check if appear duplicate pmids
		boolean duplicate = false;
		ConnectorWoodstox connector = new ConnectorWoodstox();
		BioCCollection collection = new BioCCollection();
		collection = connector.startRead(new InputStreamReader(new FileInputStream(input), "UTF-8"));
		String output_STR="";
		HashMap<String, String> annotation_hash = new HashMap<String, String>();
		HashMap<String, Integer> annotation_count_hash = new HashMap<String, Integer>();
		while (connector.hasNext()) 
		{
			BioCDocument document = connector.next();
			String PMID = document.getID();
			if(pmidlist.containsKey(PMID)){System.out.println("\n[Warning]: duplicate pmid-"+PMID);duplicate = true;}
			else{pmidlist.put(PMID,"");}
			for (BioCPassage passage : document.getPassages()) 
			{
				String passage_text=passage.getText();
				HashMap<Integer, String> annotation_mention_hash = new HashMap<Integer, String>();
				ArrayList<String> annotation_arr = new ArrayList<String>();
				int count_anno=0;
				for (BioCAnnotation annotation : passage.getAnnotations()) 
				{
					String Annotype = annotation.getInfon("type");
					int start = annotation.getLocations().get(0).getOffset();
					int last = start + annotation.getLocations().get(0).getLength();
					String AnnoMention=annotation.getText();
					if(!annotation.getInfon("identifier").isEmpty())
					{
						String Annoid = annotation.getInfon("identifier");
						annotation_mention_hash.put(start, start+"\t"+last+"\t"+AnnoMention+"\t"+Annotype+"\t"+Annoid);
					}
					else
					{
						annotation_mention_hash.put(start, start+"\t"+last+"\t"+AnnoMention+"\t"+Annotype);
					}
						
					count_anno++;
				}
				while(count_anno>0)
				{
					int max_start=0;
					for(Integer start : annotation_mention_hash.keySet())
					{
						if(start>max_start)
						{
							max_start=start;
						}
					}
					annotation_arr.add(annotation_mention_hash.get(max_start));
					annotation_mention_hash.remove(max_start);
					count_anno--;
				}
				for(int x=0;x<annotation_arr.size();x++)
				{
					String str[]=annotation_arr.get(x).split("\\t");
					int start = Integer.parseInt(str[0])-passage.getOffset();
					int last = Integer.parseInt(str[1])-passage.getOffset();
					String mention=str[2];
					String type=str[3];
					String id="";
					if(str.length==5)
					{
						id=str[4];
						type=id;
						type=type.replaceAll(":.*", "");
					}
					annotation_hash.put(type+"\t"+id,mention);
					if(!annotation_count_hash.containsKey(type+"\t"+id))
					{
						annotation_count_hash.put(type+"\t"+id,1);
					}
					else
					{
						annotation_count_hash.put(type+"\t"+id,annotation_count_hash.get(type+"\t"+id)+1);
					}
					String pre=passage_text.substring(0, start);
					String post=passage_text.substring(last, passage_text.length());
					passage_text=pre+"<font style=\"background-color: rgb("+color_hash.get(type)+")\" title='"+id+"'>"+mention+"</font>"+post;
					color_used_hash.put(type, color_hash.get(type));
				}
				output_STR=output_STR+passage_text+"<BR /><BR />\n";
			}
		}
		if(duplicate == true){System.exit(0);}
		
		BufferedWriter HTMLOutputFormat = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		HTMLOutputFormat.write("<!DOCTYPE html>\n<html><head>\n<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n<title>BioC Documents</title>\n</head><body>");
		HTMLOutputFormat.write("<table border=1><tr><td>Type</td><td>concepts (identifiers) - mentioned frequency</td></tr>");
		for(String type: color_used_hash.keySet())
		{
			HTMLOutputFormat.write("<tr style=\"background-color: rgb("+color_hash.get(type)+")\">");
			HTMLOutputFormat.write("<td>"+type+"</td>");
			HTMLOutputFormat.write("<td>");
			
			for(String typeid: annotation_hash.keySet())
			{
				String type_id[]=typeid.split("\\t");
				if(type_id[0].equals(type))
				{
					if(type_id[0].equals(type_id[1]))
					{
						HTMLOutputFormat.write(annotation_hash.get(typeid)+" - "+annotation_count_hash.get(typeid)+"<BR />");
					}
					else
					{
						HTMLOutputFormat.write(annotation_hash.get(typeid)+" ("+type_id[1]+") - "+annotation_count_hash.get(typeid)+"<BR />");
					}
				}
			}
			HTMLOutputFormat.write("</td>");
			HTMLOutputFormat.write("</tr>");
		}
		HTMLOutputFormat.write("</table><BR />");
		HTMLOutputFormat.write(output_STR);
		HTMLOutputFormat.write("</body></html>");
		HTMLOutputFormat.close();
	}
	public static void BioC2AnnotationList(String inputfolder,String output) throws IOException, XMLStreamException //AlzPED usage only
	{
		HashMap<String, Integer> annotation_hash = new HashMap<String, Integer>();
		File folder = new File(inputfolder);
		File[] listOfFiles = folder.listFiles();
		for (int i = 0; i < listOfFiles.length; i++)
		{
			if (listOfFiles[i].isFile()) 
			{
				String InputFile = listOfFiles[i].getName();
				if(InputFile.matches(".*xml"))
				{
					ArrayList<String> color_arr = new ArrayList<String>();
					HashMap<String, String> pmidlist = new HashMap<String, String>(); // check if appear duplicate pmids
					boolean duplicate = false;
					ConnectorWoodstox connector = new ConnectorWoodstox();
					BioCCollection collection = new BioCCollection();
					collection = connector.startRead(new InputStreamReader(new FileInputStream(inputfolder+"/"+InputFile), "UTF-8"));
					while (connector.hasNext()) 
					{
						BioCDocument document = connector.next();
						String PMID = document.getID();
						if(pmidlist.containsKey(PMID)){System.out.println("\n[Warning]: duplicate pmid-"+PMID);duplicate = true;}
						else{pmidlist.put(PMID,"");}
						for (BioCPassage passage : document.getPassages()) 
						{
							String passage_text=passage.getText();
							for (BioCAnnotation annotation : passage.getAnnotations()) 
							{
								String Annotype = annotation.getInfon("type");
								int start = annotation.getLocations().get(0).getOffset();
								int last = start + annotation.getLocations().get(0).getLength();
								String AnnoMention=annotation.getText();
								if(!annotation.getInfon("identifier").isEmpty())
								{
									String Annoid = annotation.getInfon("identifier");
									if(!annotation_hash.containsKey(PMID+"\t"+AnnoMention+"\t"+Annoid))
									{
										annotation_hash.put(PMID+"\t"+AnnoMention+"\t"+Annoid,1);
									}
									else
									{
										annotation_hash.put(PMID+"\t"+AnnoMention+"\t"+Annoid,annotation_hash.get(PMID+"\t"+AnnoMention+"\t"+Annoid)+1);
									}
								}
								else
								{
									if(!annotation_hash.containsKey(PMID+"\t"+AnnoMention))
									{
										annotation_hash.put(PMID+"\t"+AnnoMention,1);
									}
									else
									{
										annotation_hash.put(PMID+"\t"+AnnoMention,annotation_hash.get(PMID+"\t"+AnnoMention)+1);
									}
								}
							}
						}
					}
				}
			}
		}
		BufferedWriter OutputFormat = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		for(String anno : annotation_hash.keySet())
		{
			OutputFormat.write(anno+"\t"+annotation_hash.get(anno)+"\n");
		}
		OutputFormat.close();
	}
	public static void PDF2BioC_itextpdf(String input,String output) throws IOException, XMLStreamException
	{
		/**
		 * itext
		 */
		
		String parser = BioCFactory.WOODSTOX;
		BioCFactory factory = BioCFactory.newFactory(parser);
		BioCDocumentWriter BioCOutputFormat = factory.createBioCDocumentWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		BioCCollection biocCollection = new BioCCollection();
		ZoneId zonedId = ZoneId.of( "America/Montreal" );
		LocalDate today = LocalDate.now( zonedId );
		biocCollection.setDate(today.toString());
		biocCollection.setKey("BioC.key");//key
		biocCollection.setSource("BioC");//source
		
		BioCOutputFormat.writeCollectionInfo(biocCollection);
		BioCDocument biocDocument = new BioCDocument();
		int startoffset=0;
		int count_line=0;
		PdfReader reader = new PdfReader(input); //pdf2text
		input=input.replaceAll("[^0-9]","");
		if(input.equals("")){input="1";}
		biocDocument.setID(input);
		for (int pageNumber = 1; pageNumber <= reader.getNumberOfPages(); pageNumber++) //pdf2text
        {
			String line = PdfTextExtractor.getTextFromPage(reader, pageNumber); //pdf2text
			line=line.replaceAll("[\n\r\t]+", " ");
			if(!line.equals(""))
			{
				count_line++;
				BioCPassage biocPassage = new BioCPassage();
				Map<String, String> Infons = new HashMap<String, String>();
				Infons.put("type", "Line_"+count_line);
				biocPassage.setInfons(Infons);
				biocPassage.setText(line);
				biocPassage.setOffset(startoffset);
				startoffset=startoffset+line.length()+1;
				biocDocument.addPassage(biocPassage);
			}
        }
		biocCollection.addDocument(biocDocument);
		BioCOutputFormat.writeDocument(biocDocument);
	
		BioCOutputFormat.close();
	}
	public static void PDF2BioC(String input,String output) throws IOException, XMLStreamException
	{
		/**
		 * pdfbox
		 */
		
		File file = new File(input);
		PDFParser pdfparser = new PDFParser(new RandomAccessFile(file,"r"));  
		pdfparser.parse();
		COSDocument cosDoc = pdfparser.getDocument();
		PDFTextStripper pdfStripper = new PDFTextStripper();
		PDDocument pdDoc = new PDDocument(cosDoc);
		
		String parser = BioCFactory.WOODSTOX;
		BioCFactory factory = BioCFactory.newFactory(parser);
		BioCDocumentWriter BioCOutputFormat = factory.createBioCDocumentWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		BioCCollection biocCollection = new BioCCollection();
		ZoneId zonedId = ZoneId.of( "America/Montreal" );
		LocalDate today = LocalDate.now( zonedId );
		biocCollection.setDate(today.toString());
		biocCollection.setKey("BioC.key");//key
		biocCollection.setSource("BioC");//source
		
		BioCOutputFormat.writeCollectionInfo(biocCollection);
		BioCDocument biocDocument = new BioCDocument();
		int startoffset=0;
		int count_line=0;
		input=input.replaceAll("[^0-9]","");
		if(input.equals("")){input="1";}
		biocDocument.setID(input);
		for(int i=0;i<pdDoc.getNumberOfPages();i++)
		{
			pdfStripper.setStartPage(i);
			pdfStripper.setEndPage(i);
			String line = pdfStripper.getText(pdDoc);
			line=line.replaceAll("[^\\x09\\x0A\\x0D\\x20-\\xD7FF\\xE000-\\xFFFD\\x10000-x10FFFF]"," ");
			line=line.replaceAll("[\n\r\t]+", " ");
			if(!line.equals(""))
			{
				count_line++;
				BioCPassage biocPassage = new BioCPassage();
				Map<String, String> Infons = new HashMap<String, String>();
				Infons.put("type", "Line_"+count_line);
				biocPassage.setInfons(Infons);
				biocPassage.setText(line);
				biocPassage.setOffset(startoffset);
				startoffset=startoffset+line.length()+1;
				biocDocument.addPassage(biocPassage);
			}
		}
		
		biocCollection.addDocument(biocDocument);
		BioCOutputFormat.writeDocument(biocDocument);
		BioCOutputFormat.close();
		
		cosDoc.close();
		pdDoc.close();
	}
	public static void PDF2PubTator(String input,String output) throws IOException, XMLStreamException
	{
		/**
		 * pdfbox
		 */
		
		BufferedWriter outputfile  = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		
		File file = new File(input);
		PDFParser pdfparser = new PDFParser(new RandomAccessFile(file,"r"));  
		pdfparser.parse();
		COSDocument cosDoc = pdfparser.getDocument();
		PDFTextStripper pdfStripper = new PDFTextStripper();
		PDDocument pdDoc = new PDDocument(cosDoc);
		
		input=input.replaceAll("[^0-9]","");
		if(input.equals("")){input="1";}
		int count_line=0;
		for(int i=0;i<pdDoc.getNumberOfPages();i++)
		{
			pdfStripper.setStartPage(i);
			pdfStripper.setEndPage(i);
			String line = pdfStripper.getText(pdDoc);
			line=line.replaceAll("[\n\r\t]+", " ");
			if(!line.equals(""))
			{
				outputfile.write(input+"|Line_"+count_line+"|"+line+"\n");
				count_line++;
			}
		}
		
		outputfile.write("\n");
		outputfile.close();
		cosDoc.close();
		pdDoc.close();
	}
	public static void Excelx2PubTator(String input,String output) throws IOException, XMLStreamException
	{
		BufferedWriter outputfile  = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		FileInputStream file = new FileInputStream(new File(input));
		Workbook workbook = new XSSFWorkbook(file);
		for(int i=0;i<workbook.getNumberOfSheets();i++)
		{
			org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i);
			int j = 0;
			for (Row row : sheet)
			{
				String cells="";
				for (Cell cell : row) 
			    {
			    	cells=cells+cell.toString()+"; ";
			    }
			    j++;
			    outputfile.write((i+100000)+"|"+j+"|"+cells+"\n");
			}
			if(j>0)
			{
				outputfile.write("\n");
			}
		}
		outputfile.close();
	}
	public static void Excel2PubTator(String input,String output) throws IOException, XMLStreamException
	{
		BufferedWriter outputfile  = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		FileInputStream file = new FileInputStream(new File(input));
		Workbook workbook = new HSSFWorkbook(file);
		for(int i=0;i<workbook.getNumberOfSheets();i++)
		{
			org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i);
			int j = 0;
			for (Row row : sheet)
			{
				String cells="";
				for (Cell cell : row) 
			    {
			    	cells=cells+cell.toString()+"; ";
			    }
			    j++;
			    outputfile.write((i+100000)+"|"+j+"|"+cells+"\n");
			}
			if(j>0)
			{
				outputfile.write("\n");
			}
		}
		outputfile.close();
	}
	public static void Word2PubTator(String input,String output) throws IOException, XMLStreamException
	{
		BufferedWriter outputfile  = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		try 
		{
			FileInputStream file = new FileInputStream(new File(input));
			HWPFDocument doc = new HWPFDocument(file);
			WordExtractor we = new WordExtractor(doc);
			String[] paragraphs = we.getParagraphText();
			
			int count_para=1;
			for (String para : paragraphs) 
			{
				para=para.replaceAll("", "");
				para=para.replaceAll("[\\n\\r]+", "");
				if(!para.equals(""))
				{
					outputfile.write("1000001|"+count_para+"|"+para+"\n");
					count_para++;
				}
			}
			outputfile.write("\n");
			file.close();
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		outputfile.close();
	}
	public static void Wordx2PubTator(String input,String output) throws IOException, XMLStreamException
	{
		BufferedWriter outputfile  = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
		try 
		{
			FileInputStream file = new FileInputStream(new File(input));
			XWPFDocument document = new XWPFDocument(file);
			
			List<XWPFParagraph> paragraphs = document.getParagraphs();
			List<XWPFTable> tables = document.getTables();
			int count_para=1;
			for (XWPFParagraph para : paragraphs) 
			{
				String pa=para.getText().replaceAll("[\\n\\r]+", "");
				if(!pa.equals(""))
				{
					outputfile.write("1000001|"+count_para+"|"+pa+"\n");
					count_para++;
				}
			}
			count_para=1;
			for (XWPFTable ta : tables) 
			{
				for(int i=0;i<ta.getNumberOfRows();i++)
				{
					if (count_para == 1){outputfile.write("\n");}
					List<XWPFTableCell> cells= ta.getRow(i).getTableCells();
					String cells_str="";
					for(XWPFTableCell c : cells)
					{
						cells_str=cells_str+c.getText()+"; ";
					}
					outputfile.write("1000002|"+count_para+"|"+cells_str+"\n");
					count_para++;
				}
			}
			outputfile.write("\n");
			file.close();
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		outputfile.close();
	}
	public static void Decompression(String input,String output) throws IOException, XMLStreamException
	{
		try (TarArchiveInputStream fin = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(input))))
		{
            TarArchiveEntry entry;
            while ((entry = fin.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File curfile = new File(output, entry.getName());
                File parent = curfile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                IOUtils.copy(fin, new FileOutputStream(curfile));
            }
        }
	}
	private static TarArchiveOutputStream getTarArchiveOutputStream(String name) throws IOException 
	{
		TarArchiveOutputStream taos = new TarArchiveOutputStream(new FileOutputStream(name));
		// TAR has an 8 gig file limit by default, this gets around that
		taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
		// TAR originally didn't support long file names, so enable the support for it
		taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
		taos.setAddPaxHeadersForNonAsciiNames(true);
		return taos;
	}
	private static void addToArchiveCompression(TarArchiveOutputStream out, File file, String dir) throws IOException 
	{
        String entry = dir + File.separator + file.getName();
        if (file.isFile()){
            out.putArchiveEntry(new TarArchiveEntry(file, entry));
            try (FileInputStream in = new FileInputStream(file)){
                IOUtils.copy(in, out);
            }
            out.closeArchiveEntry();
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null){
                for (File child : children){
                    addToArchiveCompression(out, child, entry);
                }
            }
        } else {
            System.out.println(file.getName() + " is not supported");
        }
    }
	public static void main(String [] args) throws IOException, InterruptedException, XMLStreamException 
	{
		if(args.length<2)
		{
			System.out.println("\n$ java -jar FormatConverter.jar [inputfile] [outputfile] [output format:BioC|PubTator]\n");
			System.out.println("* [inputfile] and [outputfile] can be file or folder");
			System.out.println("* BioC-XML|PubTator|FreeText|PDF|MSWord|MSExcel formats are allowed in [inputfile].");
			System.out.println("* BioC are PubTator formats are allowed in [outputfile].");
			System.out.println("* BioC-XML is the default format.");
			
		}
		else
		{
			String input = args[0];
			String output= args[1];
			String format="";
			if(args.length<3)
			{
				format="BioC";
			}
			else
			{
				format= args[2];
			}
		
			File file = new File(input);

			boolean isDirectory = file.isDirectory(); // Check if it's a directory
			boolean isFile =      file.isFile();      // Check if it's a regular file
			
			ArrayList<String> inputfiles = new ArrayList<String>();
			ArrayList<String> outputfiles = new ArrayList<String>();
			if(isFile)
			{
				inputfiles.add(input);
				outputfiles.add(output);
			}
			else if(isDirectory)
			{
				File[] listOfFiles = file.listFiles();
				for (int i = 0; i < listOfFiles.length; i++)
				{
					if (listOfFiles[i].isFile()) 
					{
						String filename = listOfFiles[i].getName();
						
						File f = new File(input+"/"+filename);
						if(f.exists() && !f.isDirectory()) 
						{ 
							inputfiles.add(input+"/"+filename);
							outputfiles.add(output+"/"+filename);
						}
					}
				}
			}
			else
			{
				System.out.println("[Error]: Input file is not exist.");
			}
			
			for(int file_i=0;file_i<inputfiles.size();file_i++)
			{	
				String inputfile=inputfiles.get(file_i);
				String outputfile=outputfiles.get(file_i);
				 
				File outputf = new File(outputfile);
				if(outputf.exists() && (!outputf.isDirectory()))
				{
					System.out.println(outputfile+" - Done. (The output file exists in output folder)");
				}
				else
				{
					String FormatCheck = BioCFormatCheck(inputfile);
					
					if(FormatCheck.equals("tar.gz"))
					{
						//don't change
					}
					else if(format.equals("PubTator"))
					{
						outputfile=outputfile+".txt";
					}
					else if(format.equals("BioC"))
					{
						outputfile=outputfile+".xml";
					}
					
					System.out.println("Input Format: " + FormatCheck);
					
					if(inputfile.endsWith(".jpg") || inputfile.endsWith(".gif") || inputfile.endsWith(".png"))
					{
						System.out.println("Figure - Ignored.");
					}
					else if(FormatCheck.equals("PDF"))
					{
						if(format.equals("BioC"))
						{
							System.out.println("Format convert from PDF to BioC(XML): "+inputfile+" -> "+outputfile);
							PDF2BioC(inputfile,outputfile);
						}
						else if(format.equals("PubTator"))
						{
							System.out.println("Format convert from PDF to PubTator: "+inputfile+" -> "+outputfile);
							PDF2PubTator(inputfile,outputfile);
						}
						else
						{
							System.out.println("\n[Error 2.0]: Current output format options for PDF are : PubTator|BioC(xml)");
						}
					}
					else if(FormatCheck.equals("BioC"))
					{
						if(format.equals("PubTator"))
						{
							System.out.println("Format convert from BioC(XML) to PubTator: "+inputfile+" -> "+outputfile);
							BioC2PubTator(inputfile,outputfile);
						}
						else if(format.equals("HTML")) //with annotation only
						{
							System.out.println("Format convert from BioC(XML) to HTML: "+inputfile+" -> "+outputfile);
							BioC2HTML(inputfile,outputfile);
						}
						else if(format.equals("SciLite")) //with annotation only
						{
							System.out.println("Format convert from BioC(XML) to SciLite: "+inputfile+" -> "+outputfile);
							BioC2SciLite(inputfile,outputfile);
						}
						else
						{
							System.out.println("\n[Error 2.1]: Current output format options for BioC(XML) are : PubTator|HTML");
						}
					}
					else if(FormatCheck.equals("PubTator"))
					{
						if(format.equals("BioC"))
						{
							System.out.println("Format convert from PubTator to BioC(XML): "+inputfile+" -> "+outputfile);
							PubTator2BioC(inputfile,outputfile);
						}
						else if(format.equals("HTML")) //with annotation only
						{
							System.out.println("Format convert from PubTator to HTML: "+inputfile+" -> "+outputfile);
							PubTator2HTML(inputfile,outputfile);
						}
						else
						{
							//System.out.println("Current format options are : PubTator|BioC|HTML");
							System.out.println("\n[Error 2.2]: Current output format options for PubTator are : BioC(XML)|HTML");
						}
					}
					else if(FormatCheck.equals("Word"))
					{
						if(format.equals("PubTator"))
						{
							System.out.println("Format convert from Word to PubTator: "+inputfile+" -> "+outputfile);
							Word2PubTator(inputfile,outputfile);
						}
						else
						{
							System.out.println("\n[Error 2.4]: Current output format options for free text are : PubTator");
						}
					}
					else if(FormatCheck.equals("Wordx"))
					{
						if(format.equals("PubTator"))
						{
							System.out.println("Format convert from Word (docx) to PubTator: "+inputfile+" -> "+outputfile);
							Wordx2PubTator(inputfile,outputfile);
						}
						else
						{
							System.out.println("\n[Error 2.4]: Current output format options for free text are : PubTator");
						}
					}
					else if(FormatCheck.equals("Excel"))
					{
						if(format.equals("PubTator"))
						{
							System.out.println("Format convert from Excel to PubTator: "+inputfile+" -> "+outputfile);
							Excel2PubTator(inputfile,outputfile);
						}
						else
						{
							System.out.println("\n[Error 2.4]: Current output format options for free text are : PubTator");
						}
					}
					else if(FormatCheck.equals("Excelx"))
					{
						if(format.equals("PubTator"))
						{
							System.out.println("Format convert from Excel (xlsx) to PubTator: "+inputfile+" -> "+outputfile);
							Excelx2PubTator(inputfile,outputfile);
						}
						else
						{
							System.out.println("\n[Error 2.4]: Current output format options for free text are : PubTator");
						}
					}
					else if(FormatCheck.equals("tar.gz"))
					{
						System.out.println("Decompression : "+inputfile+" -> "+outputfile);
						Decompression(inputfile,outputfile);
					}
					else //Free Text
					{
						if(format.equals("PubTator"))
						{
							System.out.println("Format convert from FreeText to PubTator: "+inputfile+" -> "+outputfile);
							FreeText2PubTator(inputfile,outputfile);
						}
						else if(format.equals("BioC"))
						{
							System.out.println("Format convert from FreeText to BioC(XML): "+inputfile+" -> "+outputfile);
							FreeText2BioC(inputfile,outputfile);
						}
						else
						{
							System.out.println("\n[Error 2.3]: Current output format options for free text are : PubTator|BioC(XML)");
						}
					}
				}
			}
		}
	}	
}