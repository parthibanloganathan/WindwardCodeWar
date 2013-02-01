package net.windward.Windwardopolis.AI;


// Created by Windward Studios, Inc. (www.windward.net). No copyright claimed - do anything you want with this code.


import java.awt.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import net.windward.Windwardopolis.api.Company;
import net.windward.Windwardopolis.api.Map;
import net.windward.Windwardopolis.api.Passenger;
import net.windward.Windwardopolis.api.Player;

/**
 * The sample C# AI. Start with this project but write your own code as this is a very simplistic implementation of the AI.
 */
public class MyPlayerBrain implements net.windward.Windwardopolis.AI.IPlayerAI {
	// bugbug - put your team name here.
	private static String NAME = "Fighting Multiplexers";

	// bugbug - put your school name here. Must be 11 letters or less (ie use MIT, not Massachussets Institute of Technology).
	public static String SCHOOL = "Columbia U.";

	public String headingTowards = "-";

	/**
	 * The name of the player.
	 */
	private String privateName;

	public final String getName() {
		return privateName;
	}

	private void setName(String value) {
		privateName = value;
	}

	/**
	 * The game map.
	 */
	private Map privateGameMap;

	public final Map getGameMap() {
		return privateGameMap;
	}

	private void setGameMap(Map value) {
		privateGameMap = value;
	}

	/**
	 * All of the players, including myself.
	 */
	private java.util.ArrayList<Player> privatePlayers;

	public final java.util.ArrayList<Player> getPlayers() {
		return privatePlayers;
	}

	private void setPlayers(java.util.ArrayList<Player> value) {
		privatePlayers = value;
	}

	/**
	 * All of the companies.
	 */
	private java.util.ArrayList<Company> privateCompanies;

	public final java.util.ArrayList<Company> getCompanies() {
		return privateCompanies;
	}

	private void setCompanies(java.util.ArrayList<Company> value) {
		privateCompanies = value;
	}

	/**
	 * All of the passengers.
	 */
	private java.util.ArrayList<Passenger> privatePassengers;

	public final java.util.ArrayList<Passenger> getPassengers() {
		return privatePassengers;
	}

	private void setPassengers(java.util.ArrayList<Passenger> value) {
		privatePassengers = value;
	}

	/**
	 * Me (my player object).
	 */
	private Player privateMe;

	public final Player getMe() {
		return privateMe;
	}

	private void setMe(Player value) {
		privateMe = value;
	}

	private PlayerAIBase.PlayerOrdersEvent sendOrders;

	private static final java.util.Random rand = new java.util.Random();

	public MyPlayerBrain(String name) {
		setName(!net.windward.Windwardopolis.DotNetToJavaStringHelper.isNullOrEmpty(name) ? name : NAME);
	}

	/**
	 * The avatar of the player. Must be 32 x 32.
	 */
	public final byte[] getAvatar() {
		try {
			// open image
			File file = new File(getClass().getResource("/net/windward/Windwardopolis/res/avatar.gif").getFile());

			FileInputStream fisAvatar = new FileInputStream(file);
			byte [] avatar = new byte[fisAvatar.available()];
			fisAvatar.read(avatar, 0, avatar.length);
			return avatar;

		} catch (IOException e) {
			System.out.println("error reading image");
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Called at the start of the game.
	 *
	 * @param map         The game map.
	 * @param me          You. This is also in the players list.
	 * @param players     All players (including you).
	 * @param companies   The companies on the map.
	 * @param passengers  The passengers that need a lift.
	 * @param ordersEvent Method to call to send orders to the server.
	 */
	public final void Setup(Map map, Player me, java.util.ArrayList<Player> players, java.util.ArrayList<Company> companies, java.util.ArrayList<Passenger> passengers, PlayerAIBase.PlayerOrdersEvent ordersEvent) {

		try {
			setGameMap(map);
			setPlayers(players);
			setMe(me);
			setCompanies(companies);
			setPassengers(passengers);
			sendOrders = ordersEvent;

			java.util.ArrayList<Passenger> pickup = AllPickups(me, passengers);

			// get the path from where we are to the dest.
			java.util.ArrayList<Point> path = CalculatePathPlus1(me, pickup.get(0).getLobby().getBusStop());
			sendOrders.invoke("ready", path, pickup);
		} catch (RuntimeException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Called to send an update message to this A.I. We do NOT have to send orders in response.
	 *
	 * @param status     The status message.
	 * @param plyrStatus The player this status is about. THIS MAY NOT BE YOU.
	 * @param players    The status of all players.
	 * @param passengers The status of all passengers.
	 */
	public final void GameStatus(PlayerAIBase.STATUS status, Player plyrStatus, java.util.ArrayList<Player> players, java.util.ArrayList<Passenger> passengers) {

		// bugbug - Framework.cs updates the object's in this object's Players, Passengers, and Companies lists. This works fine as long
		// as this app is single threaded. However, if you create worker thread(s) or respond to multiple status messages simultaneously
		// then you need to split these out and synchronize access to the saved list objects.

		try {
			// bugbug - we return if not us because the below code is only for when we need a new path or our limo hit a bus stop.
			// if you want to act on other players arriving at bus stops, you need to remove this. But make sure you use Me, not
			// plyrStatus for the Player you are updatiing (particularly to determine what tile to start your path from).
			if (plyrStatus != getMe()) {
				Point ptDest = null;
				java.util.ArrayList<Passenger> pickup = new java.util.ArrayList<Passenger>();
				switch (status) {
				case UPDATE:
					return;
				case NO_PATH:
					break;
				case PASSENGER_NO_ACTION:
					return;
				case PASSENGER_DELIVERED:
				case PASSENGER_ABANDONED:
					
					// Yan strategic abandonment initiative
					if(getMe().getLimo().getPassenger() == null) {
						Passenger pass2 = computeMinPassenger(passengers);
						pickup.add(pass2);
						System.out.println("YAN REEVAL W/NO PASSENGER: " + pass2.getName());
						ptDest = pass2.getLobby().getBusStop();
						headingTowards = pass2.getName();
						break;
					} else {
						Passenger p = plyrStatus.getPassengersDelivered().get(plyrStatus.getPassengersDelivered().size() - 1);
						if(!getMe().getLimo().getPassenger().getEnemies().contains(p)) {
							int toPass = SimpleAStar.CalculatePath(getGameMap(), getMe().getLimo().getMapPosition(), p.getLobby().getBusStop()).size();
							int passToDest = SimpleAStar.CalculatePath(getGameMap(), p.getLobby().getBusStop(), p.getDestination().getBusStop()).size();

							boolean nearEndGame = getMe().getPassengersDelivered().size() == 7;
							// don't weight if near end of game
							double newTotalCost = (toPass + passToDest) / (nearEndGame ? 1 : ((double) p.getPointsDelivered() + 1));

							// if there's an enemy at the destination
							ArrayList<Passenger> enemies = (ArrayList<Passenger>) p.getEnemies();
							for(Passenger busp : p.getDestination().getPassengers()) {
								if(enemies.contains(busp)) newTotalCost += 10000;
							}
							
							double oldCost = SimpleAStar.CalculatePath(getGameMap(), getMe().getLimo().getMapPosition(), getMe().getLimo().getPassenger().getDestination().getBusStop()).size() / (nearEndGame ? 1 : ((double) p.getPointsDelivered() + 1));;
							
							if(newTotalCost < oldCost) {
								pickup.add(p);
								System.out.println("YAN REEVAL W/NO PASSENGER: " + p.getName());
								ptDest = p.getLobby().getBusStop();
								headingTowards = p.getName();
							}
							break;
							
						}
					}
					
					
					if(getMe().getLimo().getPassenger() != null) {
						// player just delivered to a location where my passenger wishes to go
						if(plyrStatus.getPassengersDelivered().get(plyrStatus.getPassengersDelivered().size() - 1).getLobby().getBusStop().equals(getMe().getLimo().getPassenger().getDestination().getBusStop())) {
							// the guy placed there is an enemy of my passenger
							if(getMe().getLimo().getPassenger().getEnemies().contains(plyrStatus.getPassengersDelivered().get(plyrStatus.getPassengersDelivered().size() - 1))) {
								Passenger pass2 = computeMinPassenger(passengers);
								pickup.add(pass2);
								System.out.println("REFUSAL: " + pass2.getName());
								ptDest = pass2.getLobby().getBusStop();
								headingTowards = pass2.getName();
							}
						}
					}
					break;
				case PASSENGER_REFUSED:
					break;
				case PASSENGER_DELIVERED_AND_PICKED_UP:
				case PASSENGER_PICKED_UP:
					if(plyrStatus.getLimo().getPassenger().getName().equals(headingTowards)) {
						System.out.println("SOMEBODY SET UP US THE BOMB: " + headingTowards);
						Passenger newPass = computeMinPassenger(passengers);
						pickup.add(newPass);
						ptDest = newPass.getLobby().getBusStop();
						headingTowards = newPass.getName();
						System.out.println("NOW HEADING TOWARDS: " + headingTowards);
						java.util.ArrayList<Point> path = CalculatePathPlus1(plyrStatus, ptDest);

						// update our saved Player to match new settings
						if (path.size() > 0) {
							getMe().getLimo().getPath().clear();
							getMe().getLimo().getPath().addAll(path);
						}
						if (pickup.size() > 0) {
							getMe().getPickUp().clear();
							getMe().getPickUp().addAll(pickup);
						}

						sendOrders.invoke("move", path, pickup);
					}
					break;
				default:
					throw new RuntimeException("unknown status");
				}
				return;
			}

			Point ptDest = null;
			java.util.ArrayList<Passenger> pickup = new java.util.ArrayList<Passenger>();
			switch (status) {
			case UPDATE:
				// Print stuff out for debugging
				//                	for(Passenger p : passengers) {
				//                		System.out.println("Enemy clustering: " + p.getName() + " : " + enemyClustering(getMe().getLimo().getPassenger(), players));
				//                	}
				return;
			case NO_PATH:
				if (plyrStatus.getLimo().getPassenger() == null) {
					Passenger pass = computeMinPassenger(passengers);
					pickup.add(pass);
					ptDest = pass.getLobby().getBusStop();
					headingTowards = pass.getName();
					System.out.println("no passenger, time to pick one up: " + pass.getName());
				} else{
					ptDest = plyrStatus.getLimo().getPassenger().getDestination().getBusStop();
					headingTowards = "-";
					System.out.println("continuing to passenger's destination...");
				}
				break;
			case PASSENGER_NO_ACTION:
				return;
			case PASSENGER_DELIVERED:
			case PASSENGER_ABANDONED:
				Passenger pass = computeMinPassenger(passengers);
				pickup.add(pass);
				System.out.println("delivered passenger, now going to: " + pass.getName());
				ptDest = pass.getLobby().getBusStop();
				headingTowards = pass.getName();
				break;
				//TODO FIX THIS REFUSED SHIT, and add headingTowards once it's fixed
			case PASSENGER_REFUSED:
//				Passenger pass2 = computeMinPassenger(passengers);
//				pickup.add(pass2);
//				System.out.println("OLD-REFUSAL: " + pass2.getName());
//				ptDest = pass2.getLobby().getBusStop();
//				headingTowards = pass2.getName();
				//add in random so no refuse loop
				//				for (Company cpy : getCompanies()) {
				//					if (cpy != plyrStatus.getLimo().getPassenger().getDestination()) {
				//						ptDest = cpy.getBusStop();
				//						break;
				//					}
				//				}
				break;
			case PASSENGER_DELIVERED_AND_PICKED_UP:
			case PASSENGER_PICKED_UP:
				pickup = AllPickups(plyrStatus, passengers);
				ptDest = plyrStatus.getLimo().getPassenger().getDestination().getBusStop();
				System.out.println("Picked up " + plyrStatus.getLimo().getPassenger());
				headingTowards = plyrStatus.getLimo().getPassenger().getName();
				break;
			default:
				throw new RuntimeException("unknown status");
			}

			// get the path from where we are to the dest.
			java.util.ArrayList<Point> path = CalculatePathPlus1(plyrStatus, ptDest);

			// update our saved Player to match new settings
			if (path.size() > 0) {
				getMe().getLimo().getPath().clear();
				getMe().getLimo().getPath().addAll(path);
			}
			if (pickup.size() > 0) {
				getMe().getPickUp().clear();
				getMe().getPickUp().addAll(pickup);
			}

			sendOrders.invoke("move", path, pickup);
		} catch (RuntimeException ex) {
			ex.printStackTrace();
		}
	}

	private Passenger computeMinPassenger(java.util.ArrayList<Passenger> passengers) {
		/* 
		 * Find the passenger with the minimum to-passenger+to-its-dest
		 * 
		 * */
		java.util.ArrayList<Passenger> pickup = AllPickups(getMe(), passengers);
		int minIndex = -1;
		double minValue = Double.MAX_VALUE;
		for(int i = 0; i < pickup.size(); i++) {
			Passenger p = pickup.get(i);
			int toPass = SimpleAStar.CalculatePath(getGameMap(), getMe().getLimo().getMapPosition(), p.getLobby().getBusStop()).size();
			int passToDest = SimpleAStar.CalculatePath(getGameMap(), p.getLobby().getBusStop(), p.getDestination().getBusStop()).size();

			boolean nearEndGame = getMe().getPassengersDelivered().size() == 7;
			// don't weight if near end of game
			double totalCost = (toPass + passToDest) / (nearEndGame ? 1 : ((double) p.getPointsDelivered() + 1));

			// if there's an enemy at the destination
			ArrayList<Passenger> enemies = (ArrayList<Passenger>) p.getEnemies();
			for(Passenger busp : p.getDestination().getPassengers()) {
				if(enemies.contains(busp)) totalCost += 10000;
			}

			if(totalCost < minValue) {
				minValue = totalCost;
				minIndex = i;
			}
		}
		return pickup.get(minIndex);
	}

	private java.util.ArrayList<Point> CalculatePathPlus1(Player me, Point ptDest) {
		java.util.ArrayList<Point> path = SimpleAStar.CalculatePath(getGameMap(), me.getLimo().getMapPosition(), ptDest);
		// add in leaving the bus stop so it has orders while we get the message saying it got there and are deciding what to do next.
		if (path.size() > 1) {
			path.add(path.get(path.size() - 2));
		}
		return path;
	}

	private static java.util.ArrayList<Passenger> AllPickups(Player me, Iterable<Passenger> passengers) {
		java.util.ArrayList<Passenger> pickup = new java.util.ArrayList<Passenger>();

		for (Passenger psngr : passengers) {
			// TODO at some point GET RID OF THE ME CONDITION 
			if ((!me.getPassengersDelivered().contains(psngr)) && (psngr != me.getLimo().getPassenger()) && (psngr.getCar() == null) && (psngr.getLobby() != null) && (psngr.getDestination() != null))
				pickup.add(psngr);
		}

		//add sort by random so no loops for can't pickup
		return pickup;
	}

	private double enemyClustering(Passenger ourPsnger, java.util.ArrayList<Player> players)
	{

		double distance = 0;
		Point destination = ourPsnger.getDestination().getBusStop();
		java.util.ArrayList<Double> distances = new java.util.ArrayList<Double>();

		double enemyFactor = 0;

		for(Player p: players)
		{

			Point otherTaxiDestination = p.getLimo().getPassenger().getDestination().getBusStop();
			distance = CalculatePathPlus1(p, destination).size();

			distance = distance/(getGameMap().getHeight()*getGameMap().getWidth());

			// Limo but not passenger
			if(p.getLimo().getPassenger() == null)
			{
				enemyFactor += distance;
			}
			// Limo with passenger: good for us
			/*else
			{ 
				Point goingTo = p.getLimo().getPassenger().getDestination().getBusStop();

				if (goingTo == destination)
				{
					distance = Math.sqrt((destination.getX()-goingTo.getX())*(destination.getX()-goingTo.getX())+(destination.getY()-goingTo.getY())*(destination.getY()-goingTo.getY()));
					distances.add(distance);
				}
			}*/
			else if(otherTaxiDestination == destination && ourPsnger.getEnemies().contains(p))
			{
				//coeff subject to change

				distance = distance/100;

				enemyFactor += distance;
			}
		}

		return enemyFactor;
	}

}