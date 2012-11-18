package zkGla;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.io.*;




//import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
//import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;



public class StateVector extends Gla implements java.io.Serializable {
	
	int totalProposer;
	int myProposerId;		
	int[] stateVector;
	public StateObject initialSO;
	 
	public class StateObject implements java.io.Serializable{
		int size;
		int[] list;		
	}
		
	public StateVector(String address, int totalProp, int myId){
		super(address);
		totalProposer = totalProp;
		myProposerId = myId;
		stateVector = new int[totalProposer];
		for(int i=0;i<totalProposer;i++)stateVector[i] = 0;
		initialSO = new StateObject();
		initialSO.size = totalProposer;
		initialSO.list = new int[totalProposer];
		for(int i=0;i<totalProposer;i++){
			initialSO.list[i] = 0;
		}		
	}
	
	private StateObject ByteToObj(byte[] oldByteValue){    	
    	//ByteArrayInputStream bin = new ByteArrayInputStream(oldByteValue);
    	//try {
			//ObjectInputStream in = new ObjectInputStream(bin);
			
			//String stateString = in.readUTF();
    		String stateString = new String(oldByteValue);    		
			String[] stateTokens = stateString.split(":");
			StateObject currObject = new StateObject();
			currObject.size= Integer.parseInt(stateTokens[0]);
			currObject.list=new int[currObject.size];
			for(int i=1;i<=currObject.size;i++){
				currObject.list[i-1] = Integer.parseInt(stateTokens[i]);
			}
			return currObject;				
			
		//} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			//return null;
		//}
    } 
	 
	public byte[] ObjToByte(StateObject stateObj){
    	//ByteArrayOutputStream bos = new ByteArrayOutputStream();
    	//try {
			//ObjectOutputStream out = new ObjectOutputStream(bos);
			String stateString="";
			stateString+=stateObj.size;
			for(int i=0;i<stateObj.size;i++){
				stateString+=":"+stateObj.list[i];	
			}
			return stateString.getBytes();
			//out.writeChars(stateString);
			//return bos.toByteArray();
		//} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			//return null;
		//}   	
    }
    
    
    public byte[] GetProposedValue(String updateValue){
    	StateObject stateObj = new StateObject();
    	stateObj.size = totalProposer;
    	stateObj.list = new int[totalProposer];
    	int intVal = Integer.parseInt(updateValue);
    	if(intVal > stateVector[myProposerId]){
    		stateObj.list[myProposerId] = intVal;
    	}
    	else return null;
    	for(int i=0;i<totalProposer;i++){
    		if(i!=myProposerId)stateObj.list[i] = stateVector[i]; 
    	}
    	return ObjToByte(stateObj);
    }
	
	
	private boolean CheckEquality(byte[] oldByteVal, byte[] newByteVal){
    	StateObject oldVal = ByteToObj(oldByteVal);
    	StateObject newVal = ByteToObj(newByteVal);
    	if(oldVal==null || newVal==null) return false;
    	for (int i =0;i<totalProposer;i++){
    		if(oldVal.list[i] != newVal.list[i]) return false;    		
    	}
    	return true;
    }
    
    public String PrintValue(byte[] value){
    	StateObject sObj = ByteToObj(value);
    	String output="";
    	if(sObj==null){
    		output += "Error in printing";
    		return output;
    	}
    	output+= "{";
    	for(int i=0;i<totalProposer-1;i++)
    		output += sObj.list[i] + ", ";
    	output+= sObj.list[totalProposer-1] + "}";
    	return output;
    }
              
    public byte[] JoinValue(byte[] oldByteValue, byte[] proposedByteValue){
    	StateObject newValue = new StateObject();   	
    	StateObject oldValue = ByteToObj(oldByteValue);
    	StateObject proposedValue = ByteToObj(proposedByteValue);
    	
    	if(oldValue!=null && proposedValue!=null){
	    	newValue.size = totalProposer;
	    	newValue.list = new int[totalProposer];
	    	for(int i=0;i<totalProposer;i++){
	    		if(oldValue.list[i] < proposedValue.list[i]) newValue.list[i] = proposedValue.list[i];
	    		else newValue.list[i] = oldValue.list[i];
	    	}
	    	return ObjToByte(newValue);
    	}
    	else return null;
    }
    
    public void ProposeValue(byte[] proposedByteValue){    	
    	    	
    	byte[] oldByteValue;	            	
    	Version oldVersion = new Version();    	
    	int retryCount = 20;
    	
    	
    	while(retryCount>0)
    	{    		
    		oldByteValue = ReadValue(oldVersion, root);    			
    		byte[] newByteValue;
    		
    		if(oldByteValue!=null){
    			newByteValue = JoinValue(oldByteValue,proposedByteValue);
    			if(CheckEquality(oldByteValue,newByteValue)) break;
    		    
    			if(SetValue(newByteValue, oldVersion.getVersion(), root)) {
    				//System.out.println("Value set on try no: " + (11 -retryCount));
    				break;
    			}
    		}
        	retryCount--;        	
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
    	if(args.length != 3) {
    		System.out.println("Usage: addressServer, totalNumProposers, myProposerId");
    		return;
    	}    	
    	int totalProposer = Integer.parseInt(args[1]);
    	int myId = Integer.parseInt(args[2]);
    	StateVector sVec = new StateVector(args[0], totalProposer, myId);
    	
    	String inputCmd;
    	String[] inputArgs;
    	
    	if(!sVec.TestCreateZnode(sVec.ObjToByte(sVec.initialSO), root)){
			System.out.println("Error znode can't be initialised");
			return;
		}
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
		    		byte[] value = sVec.ReadValue(version, sVec.root);
		    		if(value!=null)System.out.println(sVec.PrintValue(value));
		    		//System.out.println("Value is: " + value);
		    	}
		    	else if(inputArgs[0].equalsIgnoreCase("proposeValue")){
		    		byte[] proposeValue = sVec.GetProposedValue(inputArgs[1]);
		    		if(proposeValue!=null)sVec.ProposeValue(proposeValue);
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
