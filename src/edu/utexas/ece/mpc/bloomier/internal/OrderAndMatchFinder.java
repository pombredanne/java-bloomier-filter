package edu.utexas.ece.mpc.bloomier.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class OrderAndMatchFinder<K> {
	long hashSeed = Long.MIN_VALUE;
	BloomierHasher<K> hasher;
	
	List<K> pi;
	List<Integer> tau;
	
	OrderAndMatch<K> oam;
	
	Collection<K> keys;
	int m;
	int k;
	int q;

	public OrderAndMatchFinder(Collection<K> keys, int m, int k, int q) {
		this.keys = keys;
		this.m = m;
		this.k = k;
		this.q = q;
	}

	public OrderAndMatch<K> find(long timeoutMs) throws TimeoutException {
		final AtomicBoolean hasTimedOut = new AtomicBoolean(false);
		Timer timer = null;
		try {
			if (timeoutMs < Long.MAX_VALUE) {
				timer = new Timer(true);
				timer.schedule(new TimerTask() {
					
					@Override
					public void run() {
						hasTimedOut.set(true);
					}
				}, timeoutMs);
			}
				
			while (hashSeed <= Long.MAX_VALUE) {
				// First check for timeout
				if (hasTimedOut.get()) {
					throw new TimeoutException("Could not find order and matching for key set in alloted time with specified parameters");
				}
				
				hasher = new BloomierHasher<K>(hashSeed, m, k, q);
				
				pi = new ArrayList<K>(keys.size());
				tau = new ArrayList<Integer>(keys.size());
				
				if (findMatch(new ArrayList<K>(keys))) { // findMatch modifies key collection, so make copy
					oam = new OrderAndMatch<K>(hashSeed, pi, tau);
					break;
				}
				
				hashSeed++;
			}
			
		} finally {
			if (timer != null) {
				timer.cancel();
			}
		}
		
		return oam;
	}
	
	public boolean isFound() {
		if (oam == null) {
			return false;
		} else {
			return true;
		}
	}

	public OrderAndMatch<K> getOrderAndMatch() {
		return oam;
	}
	
	public BloomierHasher<K> getHasher() {
		return hasher;
	}

	private boolean findMatch(Collection<K> remainingKeys) {
		Queue<K> piQueue = new LinkedList<K>();
		Queue<Integer> tauQueue = new LinkedList<Integer>();
		
		SingletonFindingTweaker<K> tweaker = new SingletonFindingTweaker<K>(remainingKeys, hasher);
		
		for (K key: remainingKeys) {
			Integer iota = tweaker.tweak(key);
			if (iota != null) {
				piQueue.add(key);
				tauQueue.add(iota);
			}
		}
		
		if (piQueue.isEmpty()) {
			return false;
		}
	
		// Only pass along non-"easy" keys to next iteration
		remainingKeys.removeAll(piQueue);
		
		if (remainingKeys.isEmpty() == false) {
			if (findMatch(remainingKeys) == false) {
				return false;
			}
		}
		
		pi.addAll(piQueue);
		tau.addAll(tauQueue);
		
		return true;
	}
}