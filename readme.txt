[Background]

	Description: This program automatically detects and converts formats among three data formats: PDF, BioC, PubTator and free text for improving the program interoperability.
	Last Modification Date: January 28 2017
	Author: Chih-Hsuan Wei, Yifan Peng, Zhiyong Lu
						
[Instruction]

	$ java -jar FormatConverter.jar [inputfile] [outputfile] [output format:BioC|PubTator|HTML]

	Example: java -jar FormatConverter.jar example/23577725.PubTator example/23577725.BioC.xml BioC

	inputfile:     The name of the input file. The format of the file will be automatically detected. The formats of the input files can be PubTator, BioC, PDF, or free text. 
	               If the input text doesn't belong to PubTator/BioC/PDF, it will be treated as a free text. 
	outputfile:    The name of the output file. 
	output format: The format of the output file. The format can be BioC, PubTator, or HTML.
	
[Resources]
		BioC: http://bioc.sourceforge.net/
		PubTator: http://www.ncbi.nlm.nih.gov/CBBresearch/Lu/Demo/PubTator/