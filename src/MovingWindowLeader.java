import comp34120.ex2.PlayerImpl;
import comp34120.ex2.PlayerType;
import comp34120.ex2.Record;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A very simple leader implementation that only generates random prices
 * @author Xin
 */
final class MovingWindowLeader extends PlayerImpl {

	private float totalProfit;
	private int p_steps;
	/**
	 * You may want to delete this method if you don't want to do any
	 * initialization
	 * @param p_steps Indicates how many steps will the simulation perform
	 * @throws RemoteException If implemented, the RemoteException *MUST* be
	 * thrown by this method
	 */
	@Override
	public void startSimulation(int p_steps)
		throws RemoteException
	{
		this.p_steps = p_steps;
		totalProfit = 0;
		m_platformStub.log(m_type, "Starting simulation.");

	}


	/* The randomizer used to generate random price */
	private final Random m_randomizer = new Random(System.currentTimeMillis());

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
		// Calculate profit of the previous day
		calculateProfit(p_date - 1);

		m_platformStub.publishPrice(m_type, genPrice(1.8f, 0.05f));
	}

	// Perform all calculation related to regression here
	// R(u_l) = a_0 + a_1 * u_L
	private float followerReactionFunction() {
		return 0f;
	}

	// Calculates leaders best strategy (price) by maximising its payoff function
	// @param a1 The second parameter of follower's reaction function
	private float LeaderBestStrategy(float a1) {
			return 3/20 * a1 + 3/2;
	}

	/**
	 * Generate a random price based Gaussian distribution. The mean is p_mean,
	 * and the diversity is p_diversity
	 * @param p_mean The mean of the Gaussian distribution
	 * @param p_diversity The diversity of the Gaussian distribution
	 * @return The generated price
	 */
	private float genPrice(final float p_mean, final float p_diversity) {
		return (float) (p_mean + m_randomizer.nextGaussian() * p_diversity);
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
	}
}

