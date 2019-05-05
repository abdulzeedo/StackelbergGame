import comp34120.ex2.PlayerImpl;
import comp34120.ex2.PlayerType;
import comp34120.ex2.Record;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * A very simple leader implementation that only generates random prices
 * @author Xin
 */
final class MovingWindowLeader extends PlayerImpl {

	private float totalProfit;
	private int p_steps;
	private int windowSize = 100;
	private boolean shouldTime = true;
	private long cumulativeTime;
	private long startTime;
	private float a0;
	private float a1;
	/**
	 * You may want to delete this method if you don't want to do any
	 * initialization
	 * @param p_steps Indicates how many steps will the simulation perform
	 * @throws RemoteException If implemented, the RemoteException *MUST* be
	 * thrown by this method
	 */
	@Override
	public void startSimulation(int p_steps) throws RemoteException {
		if (shouldTime) {
			startTime = System.currentTimeMillis();
		}

		this.p_steps = p_steps;
		totalProfit = 0;
		cumulativeTime = 0;
		m_platformStub.log(m_type, "Starting simulation.");

		// Perform linear regression on all 100 historical records
		this.a0 = followerReactionFunctionParamA0(1, 100);
		this.a1 = followerReactionFunctionParamA1(1, 100);
		m_platformStub.log(m_type, "a0: " + this.a0 + " a1: " + this.a1);

		if (shouldTime) {
			long endTime = System.currentTimeMillis();
			long timeElapsed = endTime - startTime;
			cumulativeTime += timeElapsed;
		}
	}

	private MovingWindowLeader() throws RemoteException, NotBoundException {
		super(PlayerType.LEADER, "Simple Leader");
	}

	@Override
	public void goodbye() throws RemoteException {
		ExitTask.exit(500);
	}

	/**
	 * To inform this instance to proceed to a new simulation day
	 * @param p_date The date of the new day
	 * @throws RemoteException
	 */
	@Override
	public void proceedNewDay(int p_date) throws RemoteException {

		if (shouldTime) {
			startTime = System.currentTimeMillis();
		}

		// Calculate profit of the previous day
		windowSize = p_date - 2;
		float a0 = 0, a1 = 0;
		if (p_date > 101) {
			calculateProfit(p_date - 1);
			a0 = followerReactionFunctionParamA0(p_date - windowSize - 1, 
																					 p_date - 1);
			a1 = followerReactionFunctionParamA1(p_date - windowSize - 1, 
																					 p_date - 1);
		}
		else {
			a0 = this.a0;
			a1 = this.a1;
		}
		m_platformStub.log(m_type, "a0: " + a0 + " a1: " + a1);
		m_platformStub.publishPrice(m_type, leaderBestStrategy(a0, a1));

		if (shouldTime) {
			long endTime = System.currentTimeMillis();
			long timeElapsed = endTime - startTime;
			cumulativeTime += timeElapsed;
		}
	}

	// Perform all calculation related to regression here
	// R(u_l) = a_0 + a_1 * u_L
	private float followerReactionFunctionParamA0(int t, int T) throws RemoteException {
		float a0 = (float) (sumSquareX(t, T) * sumY(t, T) - sumX(t, T) * sumXTimesY(t, T))
			/ (float) (T * sumSquareX(t, T) - Math.pow(sumX(t, T), 2) );
		return a0;
	}

	// Perform all calculation related to regression here
	// R(u_l) = a_0 + a_1 * u_L
	private float followerReactionFunctionParamA1(int t, int T) throws RemoteException {
		float a1 = (float) (T * sumXTimesY(t, T) - sumX(t, T) * sumY(t, T)) 
			/ (float) (T * sumSquareX(t, T) - Math.pow(sumX(t, T), 2)); 
		return a1;
	}


	/** Calculates leaders best strategy (price) by maximising its payoff function
	* @param a1 The second parameter of follower's reaction function
	*/
	private float leaderBestStrategy(float a0, float a1) {
		return (float) (-3 - 0.3f * a0 + 0.3f * a1) / (float) (2 * 0.3f * a1 - 2);
			// return (float) 3/20 * a1 + (float) 3/2;
	}


	/** Calculates the sum of all x(t) from t to T Σx(t)
	*	@param t starting point
	* @param T ending point
	*/
	private float sumX(int t, int T) throws RemoteException {
	  float sum = 0f;
	  for (int i = t; i <= T; i++) {
	  	Record record = m_platformStub.query(m_type, i);
	    sum += record.m_leaderPrice;
	  }
	  return sum;
	}

	/** Calculates the sum of all y(t) from t to T Σy(t)
	*	@param t starting point
	* @param T ending point
	*/
	private float sumY(int t, int T) throws RemoteException {
	  float sum = 0f;
	  for (int i = t; i <= T; i++) {
	  	Record record = m_platformStub.query(m_type, i);
	    sum += record.m_followerPrice;
	  }
	  return sum;
	}

	/** Calculates the sum of all (x(t))^2 from t to T Σ(x(t))^2
	*	@param t starting point
	* @param T ending point
	*/
	private float sumSquareX(int t, int T) throws RemoteException {
	  float sum = 0f;
	  for (int i = t; i <= T; i++) {
	  	Record record = m_platformStub.query(m_type, i);
	    sum += Math.pow(record.m_leaderPrice, 2);
	  }
	  return sum;
	}

	/** Calculates the sum of all x(t)*y(t) from t to T Σ {x(t) * y(t)}
	*	@param t starting point
	* @param T ending point
	*/
	private float sumXTimesY(int t, int T) throws RemoteException {
	  float sum = 0f;
	  for (int i = t; i <= T; i++) {
	    Record record = m_platformStub.query(m_type, i);
	    sum += record.m_leaderPrice * record.m_followerPrice;
	  }
	  return sum;
  }	


	private void calculateProfit(int previousDayDate) throws RemoteException{
			Record record = m_platformStub.query(m_type, previousDayDate);

			float demand = 2 - record.m_leaderPrice + 0.3f * record.m_followerPrice;
			float profit = (record.m_leaderPrice - record.m_cost) * demand;

			m_platformStub.log(m_type, previousDayDate + ") Profit " + profit);
			totalProfit += profit;
	}

	public static void main(final String[] p_args) throws RemoteException, NotBoundException {
		new MovingWindowLeader();
	}

	/**
	 * The task used to automatically exit the leader process
	 * @author Xin
	 */
	private static class ExitTask extends TimerTask {

		static void exit(final long p_delay) {
			(new Timer()).schedule(new ExitTask(), p_delay);
		}

		@Override
		public void run() {
			System.exit(0);
		}
	}

	@Override
	public void endSimulation()
		throws RemoteException
	{
		// Calculate last day's profit
		calculateProfit(100 + p_steps);
		m_platformStub.log(m_type, "Cumulative profit: " + totalProfit);
		m_platformStub.log(m_type, "Time Elapsed: " + cumulativeTime);
	}
}

