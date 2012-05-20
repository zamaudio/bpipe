/*
 * Copyright (c) 2012 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe

import java.util.logging.Logger;


/**
 * Data structure to track information about each tool in the tool database
 * @author ssadedin
 */
class Tool {
	
    private static Logger log = Logger.getLogger("bpipe.Tool");
    
	Tool(String name, ConfigObject obj) {
		this.probeCommand = obj.probe
		this.name = name
		log.info "Configured meta data about tool $name" 
		
		meta += obj
	}
	
	/**
	 * Name of this tool as mentioned in the config file
	 */
	String name
	
	/**
	 * Version of the tool as determined by probe command.
	 * Null until the tool has actually been probed (so only 
	 * valid if probed and probeSucceeded are true)
	 */
	String version = null
	
	/**
	 * Executable command that returns the version of the tool
	 */
	String probeCommand
	
	/**
	 * Set to true after the tool has been probed for version information
	 */
	boolean probed = false
	
	/**
	 * Set to true if the probe was executed and succeeded
	 */
	boolean probeSucceeded =false
	
	/**
	 * Miscellaneous open ended meta data about the tool
	 */
	Map<String,Object> meta = [:]
	
	/**
	 * Executes the probe command to determin
	 * 
	 * @param hostCommand
	 */
	void probe(String hostCommand) {
		
		if(probed) 
			return
			
		String binary = expandToolName(hostCommand)
		log.info "Binary for $name expanded to $binary"
		
		String realizedCommand = probeCommand.replaceAll("%bin%", binary)
		
		log.info "Probing version of tool using probe command $realizedCommand"
		
		Process process = Runtime.getRuntime().exec((String[])(['bash','-c',"$realizedCommand"].toArray()))
		StringWriter output = new StringWriter()
		process.consumeProcessOutput(output, System.err)
		int exitCode = process.waitFor()
		if(exitCode == 0) {
			version = output.toString()
			probeSucceeded = true
		}
		else 
			version = "Unable to determine version (error occured, see log)" 
	}
	
	static String NON_PATH_TOKEN = " \t;"
	
	/**
	 * Find the tool name in the host command and then 
	 * expand it to include all tokens that are legitimate 
	 * parts of a path name, thus for tools represented by an 
	 * absolute path, returning the full absolute path, and for
	 * those implicitly in the default path already, just returning
	 * the raw tool name. 
	 * <p>
	 * In addition to this behavior, a special exception is made for JAR
	 * files which expands them to include the ".jar" extension so that 
	 * the entry in bpipe.config can be without ".jar" but the actual
	 * probed file will include the full name with absolute path AND 
	 * the .jar extension.
	 * 
	 * @param hostCommand
	 * @return
	 */
	String expandToolName(String hostCommand) {
		int index = indexOfTool(hostCommand, name)
		int origIndex = index
		assert index >= 0 : "Tool name $name is expected to be part of command $hostCommand"
		if(index <0) {
			log.error("Internal error: Tool $name was probed but could not be found in the command the triggered it to be probed: $hostCommand")
			return name
		}
		
		while(index>0 && NON_PATH_TOKEN.indexOf(hostCommand.charAt(index-1) as int)<0)
			--index
		
		if(hostCommand.indexOf(name+".jar") == origIndex)
			return hostCommand.substring(index, origIndex+name.size()+4)
		else
			return hostCommand.substring(index, origIndex+name.size())
	}
	
	static String DELIMITERS = '/ \t;'
	
	static int indexOfTool(String command, String name) {
		int start = 0
		while(true) {
			int index = command.indexOf(name, start)
			if(index < 0)
				return -1
			
			String jarredName = name
			if(command.indexOf(name+".jar") == index) {
				jarredName = name + ".jar"
			}
			
			start = index + name.size()
			
			if(index>0) {
				if(DELIMITERS.indexOf(command.charAt(index-1) as int)<0)
					continue
			}
			if(index<command.size()-1-name.size()) {
				if(DELIMITERS.indexOf(command.charAt(index+jarredName.size()) as int)<0)
					continue
			}
			return index
		}
		return -1
	}
}

/**
 * Database of tools, indexed by tool name.  Tracks the version of each tool
 * that we can figure out by probing
 * 
 * @author ssadedin
 */
@Singleton
class ToolDatabase {
	
	ConfigObject config
	
	Map<String,Tool> tools = [:]

    private static Logger log = Logger.getLogger("bpipe.ToolDatabase");
    
	
	/**
	 * Initializes the tool database with the given configuration
	 * 
	 * @param parentConfig
	 */
	void init(ConfigObject parentConfig) {
		if(parentConfig.containsKey("tools")) {
			log.info "Loading tool database from user configuration"
			config = parentConfig.get("tools")
			config.each { key, value ->
				tools[key] = new Tool(key, value)
			}
		}
	}
	
	/**
	 * Probe the given command to see if it contains any tools that should be
	 * added to documentation.
	 */
	Map<String,Tool> probe(String command) {
		def result = [:]
		tools.each { String name, Tool tool ->
			if(!commandContainsTool(command,name))
				return
			
			tool.probe(command)
			
			result[name] = tool
		}
		
		return result?:null
	}
	
	/**
	 * Return true if the given command contains a reference to a program
	 * with the specified name, in a plausible context such that it might
	 * be executed.
	 * 
	 * @param command
	 * @param name
	 * @return
	 */
	boolean commandContainsTool(String command, String name) {
		return Tool.indexOfTool(command,name) > -1
	}
	
}
