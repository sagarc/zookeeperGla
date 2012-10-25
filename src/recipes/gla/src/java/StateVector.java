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



public class StateVector extends Gla {
	
	int totalProposer;
	int myProposerId;		
	int[] stateVector;
	StateObject initialSO;
	
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
    	ByteArrayInputStream bin = new ByteArrayInputStream(oldByteValue);
    	try {
			ObjectInputStream in = new ObjectInputStream(bin);			
			try {
				return (StateObject)in.readObject();
				
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
    } 
	
	private byte[] ObjToByte(StateObject stateObj){
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
    	try {
			ObjectOutputStream out = new ObjectOutputStream(bos);
			out.writeObject(stateObj);
			return bos.toByteArray();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}   	
    }
    
    
    private byte[] GetProposedValue(String val){
    	StateObject stateObj = new StateObject();
    	stateObj.size = totalProposer;
    	stateObj.list = new int[totalProposer];
    	int intVal = Integer.parseInt(val);
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
    
    private void PrintValue(byte[] value){
    	StateObject sObj = ByteToObj(value);
    	if(sObj==null){
    		System.out.println("Error in printing");
    		return;
    	}
    	System.out.print("{");
    	for(int i=0;i<totalProposer-1;i++)
    		System.out.print(sObj.list[i] + ", ");
    	System.out.println(sObj.list[totalProposer-1] + "}");
    }
              
    public byte[] JoinValue(byte[] oldByteValue, byte[] proposedByteValue){
    	StateObject newValue = new StateObject();
    	StateObject oldValue;
    	StateObject proposedValue;
    	oldValue = ByteToObj(oldByteValue);
    	proposedValue = ByteToObj(proposedByteValue);
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
    	int retryCount = 10;
    	while(retryCount>0)
    	{	
    		if(!TestCreateZnode(ObjToByte(initialSO))){
    			System.out.println("Error znode can't be initialised");
    			return;
    		}
    		oldByteValue = ReadValue(oldVersion);  		
    			
    		byte[] newByteValue;
    		//System.out.println("oldversion" + oldVersion.getVersion());
    		if(oldByteValue!=null){
    			newByteValue = JoinValue(oldByteValue,proposedByteValue);
    			if(CheckEquality(oldByteValue,newByteValue)) break;
    		    
    			if(SetValue(newByteValue, oldVersion.getVersion())) break;
    		}
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
    	if(args.length != 3) {
    		System.out.println("Usage: addressServer, totalNumProposers, myProposerId");
    		return;
    	}    	
    	int totalProposer = Integer.parseInt(args[1]);
    	int myId = Integer.parseInt(args[2]);
    	StateVector sVec = new StateVector(args[0], totalProposer, myId);
    	
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
		    		byte[] value = sVec.ReadValue(version);
		    		if(value!=null)sVec.PrintValue(value);
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
