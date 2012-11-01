package zkGla;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;






public class StateInt extends Gla {
	public StateInt(String address){
		super(address);
	}
	
	private String DecodeValue(byte[] oldByteValue){    	
    	String oldValue = new String(oldByteValue);
    	return oldValue;    	
    }    
    
    private byte[] GetProposedValue(String val){
    	return val.getBytes();
    }
	
	
	private boolean CheckEquality(byte[] oldByteVal, byte[] newByteVal){
    	String oldStringVal = new String(oldByteVal);
    	String newStringVal = new String(newByteVal);
    	int oldVal = Integer.parseInt(oldStringVal);
    	int newVal = Integer.parseInt(newStringVal);
    	if(oldVal == newVal) return true;
    	else return false;
    }
    
    private void PrintValue(byte[] value){
    	String val = new String(value);
    	System.out.println("Value is:" + val);
    }
    
    public byte[] JoinValue(byte[] oldByteValue, byte[] proposedByteValue){
    	String newValue;
    	String oldValue;
    	String proposedValue;
    	oldValue = DecodeValue(oldByteValue);
    	proposedValue = DecodeValue(proposedByteValue);
    	
    	if(((int)Integer.parseInt(oldValue)) < (int)(Integer.parseInt(proposedValue))) 
    		newValue = proposedValue;
    	else newValue = oldValue;
    	return newValue.getBytes();
    }
    
    public void ProposeValue(byte[] proposedByteValue){    	
    	    	
    	byte[] oldByteValue;	            	
    	Version oldVersion = new Version();    	
    	int retryCount = 10;
    	while(retryCount>0)
    	{	            		
    		oldByteValue = ReadValue(oldVersion, root);  		
    			 		
    		//System.out.println("oldversion" + oldVersion.getVersion());
    		
    		byte[] newByteValue = JoinValue(oldByteValue,proposedByteValue);
    		if(CheckEquality(oldByteValue,newByteValue)) break;
    			                		                	
        	if(SetValue(newByteValue, oldVersion.getVersion(), root)) break;
        	retryCount--;
        	System.out.println("Retrying to set data");
        }
    	if(retryCount == 0 ){
    		System.out.println("Value couldn't be written");
    	}          
	              
     }
	
    
    public static boolean ParseOptions(String[] args) {
        List<String> argList = Arrays.asList(args);
        Iterator<String> it = argList.iterator();
        if(argList.size() == 0) return false;
        String cmd = it.next();        
        if(cmd.equalsIgnoreCase("readValue") && argList.size()==1){
        	return true;
        }
        else if(cmd.equalsIgnoreCase("proposeValue") && argList.size()==2){        	
        	return true;
        }
        else if(cmd.equalsIgnoreCase("quit")){
        	return true;
        }
        return false;
    }     	
        
	
	public static void main(String args[]){
    	System.out.println("Enter proposedValues, -1 to stop");
    	BufferedReader br = new BufferedReader(new InputStreamReader(System.in));    	
    	if(args.length != 1) {
    		System.out.println("Usage: address of server as parameter");
    		return;
    	}
    	StateInt sInt = new StateInt(args[0]);
    	
    	String inputCmd;
    	String[] inputArgs;
    	while(true){
	    	try {
	    		inputCmd = "";
				inputCmd = br.readLine();				
				inputArgs = inputCmd.split("\\s");				
				if(!ParseOptions(inputArgs)){
					System.out.println("Usage:");
					System.out.println("	proposeValue val");
					System.out.println("	readValue");
					System.out.println("	quit");
					continue;
				}
				if(inputArgs[0].equalsIgnoreCase("readValue")){
					Version version = new Version();
		    		byte[] value = sInt.ReadValue(version, sInt.root);
		    		sInt.PrintValue(value);
		    		//System.out.println("Value is: " + value);
		    	}
		    	else if(inputArgs[0].equalsIgnoreCase("proposeValue")){
		    		byte[] proposeValue = sInt.GetProposedValue(inputArgs[1]);
		    		sInt.ProposeValue(proposeValue);
		    	}
		    	else if(inputArgs[0].equalsIgnoreCase("quit")){
		    		break;
		    	}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}    	
    	}    	
    }

}
