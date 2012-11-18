package test;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

import zkGla.ILatticeValue;
import zkGla.StateVector;
import zkGla.StateVectorImplicitJoin;
import zkGla.ILatticeValue.Version;
import zkGla.StateVectorImplicitJoin.StateObject;

public class GenerateLoadImplicitJoin {
	
	
	
	public int totalProposers;
	public String address;
	public StateVectorImplicitJoin stateVecImJInitial;
	
	public GenerateLoadImplicitJoin(int to, String ad){
		totalProposers = to;
		address = ad;
		stateVecImJInitial = new StateVectorImplicitJoin(address, totalProposers, 0);
	
	}
	
	
	static class theLock extends Object {
	   }
	static public theLock lockObject = new theLock();
	
	class GlaThread extends Thread{
		public int totalOperations;
		public int updatePercent;
		public int myId;
		public StateVector stateVec;
		public StateVectorImplicitJoin stateVecImJ;
		
		GlaThread(){}
		GlaThread(int to, int up, int id, String add, int totProp, String name){
			super(name);
			totalOperations = to;
			updatePercent = up;
			myId = id;						
			stateVecImJ = new StateVectorImplicitJoin(add, totProp, id);
			start();
		}
		
		public void run(){		
			String threadName =getName(); 
			System.out.println("Thread name "+threadName);
			Random rand = new Random();
			Random rand1 = new Random();
			FileWriter fstream=null;
			try {
				fstream = new FileWriter("analysis"+myId);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			BufferedWriter out = new BufferedWriter(fstream);
			
			long startTime = System.currentTimeMillis();
			for(int j=0;j<totalOperations;j++){				 				 
				 int decide  = rand.nextInt(100);
				 if(decide > updatePercent){				 
			    	 
			    	 byte[] value = stateVecImJ.ReadValue(stateVecImJ.root);
			    	/* if(value!=null)
						try {
							//synchronized(lockObject){
								out.write(stateVecImJ.PrintValue(value)+"\n");
							//}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						*/
				 }
				 else{					 
					 int updateValue  = j;					 
			    	 byte[] proposeValue = stateVecImJ.GetProposedValue(((Integer)updateValue).toString());
			    	 if(proposeValue!=null)stateVecImJ.ProposeValue(proposeValue);
				}
			}
			long endTime = System.currentTimeMillis();
			long diffTime = endTime - startTime;
			System.out.println("Time for " + threadName +" is: "+ diffTime);
			try {
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	public static void main(String args[]){    	
		
		if(args.length != 3) {
    		System.out.println("Usage: addressServer, totalNumProposers, configFile");
    		return;
    	}		
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(args[2]));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			System.out.println("config file not found");
			e1.printStackTrace();
			return;
		}   	
    	
		
    	int totalProposers = Integer.parseInt(args[1]);
    	String address = args[0];
    	GenerateLoadImplicitJoin gLoad = new GenerateLoadImplicitJoin(totalProposers,address);
    	
    	
    	if(!gLoad.stateVecImJInitial.TestCreateZnode(gLoad.stateVecImJInitial.ObjToByte(
    			gLoad.stateVecImJInitial.initialSO), 
    			gLoad.stateVecImJInitial.root)){
			System.out.println("Error znode can't be initialised");
			return;
		}
    	    	
    	for(int i=0;i<totalProposers;i++){
    		String param="";
			try {
				//System.out.println("Enter: totalOperation, updatePercent"); 
				param = br.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		String[] parameters = param.split("\\s");
    		int totalOperations = Integer.parseInt(parameters[0]);
    		int updatePercent = Integer.parseInt(parameters[1]);
    		gLoad.new GlaThread(totalOperations,
    				updatePercent, i, address, totalProposers,"Thread"+i );
    		try{
    			Thread.sleep(1000);    		
    		}catch(Exception e){
    			System.out.println("Thread Sleeping");
    		}
    	}
	}	
}
