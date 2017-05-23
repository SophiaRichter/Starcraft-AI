import java.util.concurrent.ThreadLocalRandom;

import com.google.common.collect.Range;

public class XCS {
	private int action = 0; //action to use for AI
 	public ClassifierSet MS = new ClassifierSet();
	public double[] PA;
	public ClassifierSet AS = new ClassifierSet();
	public ClassifierSet Pop = new ClassifierSet();
	public ClassifierSet ASOld = new ClassifierSet();	
	public int pOld = 0;	
	public Range<Double> envOld = Range.closedOpen(0.0, 100.0);
	public Range<Double> env = Range.closedOpen(0.0, 100.0);
	
	public int process(Range<Double> environment)
	{
		env = environment;
		MS = GenMatchSet(Pop, env);
		PA = GenPredictionArray(MS);
		action = SelectAction();
		AS = GenActionSet(MS,action);
		//now execute action 
		return action;
	}		
	
	public boolean profit(int p) //get reward rp
	{	
		double P = 0.0f;
		
		if (!ASOld.isEmpty())
		{
			P = pOld + Constants.gamma * GetMax(PA);
			ASOld = UpdateSet(ASOld, P);
			//GeneticAlgorithm(ASOld, envOld); not yet
		}
		//if (rp: eop) //on end of program
		//{
		//	UpdateSet(AS, P);
		//	//GeneticAlgorithm(AS,env); not yet
		//	ASOld = new ClassifierSet();
		//}
		//else
		//{
			ASOld = AS;
			pOld = p;
			envOld = env;	
		//}

		return true;
	}
	
	public double GetMax(double[] PA)
	{
		double max = 0.0f;
		int index = 0;
		
		for (int i=0;i<PA.length;i++)
		{
			if (max < PA[i])
			{
				max = PA[i];
				index = i;
			}
		}
		return PA[index];
	}
	
	private ClassifierSet GenActionSet(ClassifierSet M, int act) 
	{
		ClassifierSet A = new ClassifierSet();
		for (Classifier cl : M.clSet)
		{
			if (cl.A == act)
			{
				A.add(cl);
			}
		}
		return A;
	}

	private int SelectAction() {
		int rand = ThreadLocalRandom.current().nextInt(0, 1); //TODO check if interval correct
		
		if (rand < Constants.pExplr) 
		{
			int breaker = 0;
			while (breaker < 10)
			{
				rand = ThreadLocalRandom.current().nextInt(0, PA.length);
				if (PA[rand] != 0.0f)
				{
					return rand;
				}
				breaker++;
			}
		} 
		else 
		{
			double max = 0;
			int index = 0;
			for (int i = 0;i<PA.length;i++)
			{
				if (PA[i] > max) 
				{
					PA[i] = max;
					index = i;
				}
			}
			
			return index; //return best action	
		}
		return ThreadLocalRandom.current().nextInt(0, PA.length);
	}

	private double[] GenPredictionArray(ClassifierSet M) {
		PA  = new double[Constants.possibleActions.length];
		double[] FArray = new double[PA.length];
		
		for (Classifier cl : M.clSet)
		{
			if (PA[cl.A] == 0.0f) //TODO correct?
			{
				PA[cl.A] = cl.p * cl.F;
			}
			else PA[cl.A] = PA[cl.A] + cl.p * cl.F;
			
			FArray[cl.A] = FArray[cl.A] + cl.F;
		}
		
		for (int i=0;i<Constants.possibleActions.length;i++)
		{
			if (FArray[i] != 0)
			{
				PA[i] = PA[i] / FArray[i];
			}
		}			
		return PA;
	}

	
	
	public ClassifierSet UpdateSet(ClassifierSet A,double P)
	{
		for (Classifier cl : A.clSet)
		{
			cl.exp++;
			//update prediction
			if (cl.exp < 1/Constants.beta)
			{
				cl.p = cl.p + (P - cl.p);
			}
			else
			{
				cl.p = cl.p + Constants.beta * (P - cl.p);
			}
			//update prediction error
			if(cl.exp < 1/Constants.beta)
			{
				cl.e = cl.e + (Math.abs(P - cl.p)-cl.e) / cl.exp;
			}
			else
			{
				cl.e = cl.e + Constants.beta * (Math.abs(P-cl.p)-cl.e);
			}
			
			//update action set size estimate
			if (cl.exp < 1/Constants.beta)
			{
				int sumNumerosity = 0;
				for (Classifier c : A.clSet) sumNumerosity += c.n; 
				cl.as = cl.as + (sumNumerosity - cl.as) / cl.exp;
			}
			else
			{
				int sumNumerosity = 0;
				for (Classifier c : A.clSet) sumNumerosity += c.n; 
				cl.as = cl.as + Constants.beta * (sumNumerosity - cl.as);
			}
		}
		
		A = UpdateFitness(A);
		if (Constants.doSubsumption) //shouldnt be a constant?
		{
			ASSubsumption(A);
		}
		return A;
	}
	
	private void ASSubsumption(ClassifierSet A) //doActionSetSubsumption
	{ 
		Classifier cl = new Classifier();
		
		for (Classifier c : A.clSet)
		{
			if (couldSubsume(c))
			{
				if(cl == null || (c.countWC() > cl.countWC())) //count wildcards in c.Condition
				{
					cl = c;
				}	
			}
		}
		if (cl != null)
		{
			for (Classifier c : A.clSet)
			{
				if (cl.moreGeneral(c))
				{
					cl.n = cl.n + c.n;
					A.removeCl(cl);
					Pop.removeCl(cl);
				}
				
			}
		}
	}

	private boolean couldSubsume(Classifier c) {
		if (c.exp > Constants.ThetaSub && c.e < Constants.epsilon0) return true;
		return false;
	}

	private ClassifierSet UpdateFitness(ClassifierSet A) 
	{
		double accuracySum = 0;
		for (Classifier cl : A.clSet)
		{
			if (cl.e < Constants.epsilon0)
			{
				cl.kapa = 1;
			}
			else
			{
				cl.kapa = Constants.alpha * Math.pow((cl.e / Constants.epsilon0),-Constants.nu);
			}
			accuracySum = accuracySum + cl.kapa * cl.n; //TODO correct? kapa mby zero
		}
		
		for (Classifier cl : A.clSet) cl.F = cl.F + Constants.beta * (cl.kapa * cl.n / accuracySum - cl.F);
		
		return A;
	}

	public ClassifierSet GenMatchSet(ClassifierSet Popu,Range<Double> env)
	{
		ClassifierSet M = new ClassifierSet();
		while (M.isEmpty())
		{
			for (Classifier cl : Popu.clSet)
			{
				if (cl.doesMatch(env)) M.add(cl);
			}
			
			if (M.GetDA() < Constants.ThetaMna) //count distinct actions in M 
			{
				Pop.add(Covering(M, env)); //cover and add to Pop
				DeleteFromPop(Popu); //delete some entries with certain probability
				M = new ClassifierSet();
			}
			return M;
		}
		return M; 
	}
	
	public void DeleteFromPop(ClassifierSet Popu)
	{
		int sumNumerosity = 0;
		double sumFitness = 0;
		for (Classifier c : Popu.clSet) 
			{
				sumNumerosity += c.n; 	
				sumFitness += c.F;
			}
		
		if (sumNumerosity <= Constants.N) return;
		
		double avgFitInPop = sumFitness / sumNumerosity;
		double voteSum = 0;
		
		for (Classifier c : Pop.clSet)
		{
			voteSum = voteSum + deletionVote(c,avgFitInPop);
			if (voteSum > Constants.ChoicePoint)
			{
				if (c.n > 1) 
				{
					c.n--;
				}
				else Pop.removeCl(c);
			}
		}
	}
	
	public double deletionVote(Classifier cl, double avg)
	{
		double vote = cl.as * cl.n;
		if(cl.exp > Constants.ThetaDel && cl.F /cl.n < Constants.delta * avg)
		{
			vote = vote * avg / (cl.F / cl.n);
		}
		return vote;
	}
	
	public Classifier Covering(ClassifierSet M, Range<Double> env)
	{
		Classifier cl = new Classifier();
		
		int rand = ThreadLocalRandom.current().nextInt(1, 100); //better Math.random? TODO check interval
		cl.C = Range.open(env.lowerEndpoint() - rand/2, env.upperEndpoint() + rand/2);
		
//		for (int i = 0;i<cl.C.length();i++) //for each char in Condition
//		{
//			char[] ch = cl.C.toCharArray();
//			int rand = ThreadLocalRandom.current().nextInt(0, 1); //better Math.random? TODO check interval
//			if (rand < Constants.Pr) ch[i] = '#'; //P# probability to insert a #
//			else 
//			{
//				ch[i] = env.charAt(i);
//			}
//			cl.C = ch.toString();
//		}
		
		cl.A = M.getUnusedAction();
		cl.p = Constants.pI; //initial p
		cl.e = Constants.eI; //inital e
		cl.F = Constants.FI; //inital F
		cl.exp = 0;
		//cl.ts = t; //TODO current frame?
		cl.as = 1;
		cl.n = 1;
		
		return cl;	
	}

	public int getAction() {
		return action;
	}
	
	
}
