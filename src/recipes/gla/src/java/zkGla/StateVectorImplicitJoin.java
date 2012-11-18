package zkGla;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;






//import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
//import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;



public class StateVectorImplicitJoin extends Gla {
	
	int totalProposer;
	int myProposerId;		
	int[] stateVector;
	public StateObject initialSO;
	 
	public class StateObject{
		public int size;
		public int[] list;		
	}
		
	public StateVectorImplicitJoin(String address, int totalProp, int myId){
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
		String stateString = new String(oldByteValue);    		
		String[] stateTokens = stateString.split(":");
		StateObject currObject = new StateObject();
		currObject.size= Integer.parseInt(stateTokens[0]);
		currObject.list=new int[currObject.size];
		for(int i=1;i<=currObject.size;i++){
			currObject.list[i-1] = Integer.parseInt(stateTokens[i]);
		}
		return currObject;
	}
	 
	public byte[] ObjToByte(StateObject stateObj){
		String stateString="";
		stateString+=stateObj.size;
		for(int i=0;i<stateObj.size;i++){
			stateString+=":"+stateObj.list[i];	
		}
		return stateString.getBytes();			   	
    }
    
    //value to be proposed by using in memory stateVector
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
    
    
    public void ProposeValue(byte[] proposedByteValue){    	    	
    	byte[] newByteValue;		
		newByteValue = ProposeData(proposedByteValue, -1, root);
		if(newByteValue == null) {
			System.out.println("Error in accepting proposed value");			
		}
		else{
			//System.out.println("P:" + PrintValue(newByteValue));
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
    	BufferedReader br = new BufferedReader(new InputStreamReader(System.in));    	
    	if(args.length != 3) {
    		System.out.println("Usage: addressServer, totalNumProposers, myProposerId");
    		return;
    	}    	
    	int totalProposer = Integer.parseInt(args[1]);
    	int myId = Integer.parseInt(args[2]);
    	StateVectorImplicitJoin sVec = new StateVectorImplicitJoin(args[0], totalProposer, myId);
    	
    	String inputCmd;
    	String[] inputArgs;
    	System.out.println("Enter proposedValues, -1 to stop");
    	
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
		    		byte[] value = sVec.ReadValue(Gla.root);
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

	@Override
	public byte[] JoinValue(byte[] ov, byte[] nv) {
		// TODO Auto-generated method stub
		return null;
	}

}
