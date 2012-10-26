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
import zkGla.ILatticeValue.Version;

public class GenerateLoad {
	
	

	public int totalProposers;
	public String address;	
	
	class GlaThread extends Thread{
		public int totalOperations;
		public int updatePercent;
		public int myId;
		public StateVector stateVec;
		GlaThread(){}
		GlaThread(int to, int up, int id, String add, int totProp, String name){
			super(name);
			totalOperations = to;
			updatePercent = up;
			myId = id;
			//System.out.println(add + " "+id+" "+name);
			stateVec = new StateVector(add, totProp, id);
			start();
		}
		
		public void run(){
			System.out.println("Thread name "+getName());
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
			
			for(int i=0;i<totalOperations;i++){
				 				 
				 int decide  = rand.nextInt(100);
				 if(decide > updatePercent){
					 //Version version = new Version();
			    	 byte[] value = stateVec.ReadValue();
			    	 if(value!=null)
						try {
							out.write(stateVec.PrintValue(value));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
				 }
				 else{
					 //int updateValue = rand1.nextInt(10*totalOperations);
					 int updateValue  = i;
					 byte[] proposeValue = stateVec.GetProposedValue(((Integer)updateValue).toString());
			    	 if(proposeValue!=null)stateVec.ProposeValue(proposeValue);					 
				}
			}
			try {
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	//public GlaThread[] proposerThread;
	
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
    	GenerateLoad gLoad = new GenerateLoad();
    	int totalProposers = Integer.parseInt(args[1]);
    	String address = args[0];
    	    	
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
    	}
	}	
}
