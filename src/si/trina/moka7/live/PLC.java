package si.trina.moka7.live;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.sourceforge.snap7.moka7.S7;
import com.sourceforge.snap7.moka7.S7Client;

public class PLC implements Runnable {

	public ArrayList<PLCListener> listeners;
	public Object PLCSyncObj;

	private int plcToPcDb;
	private int pcToPlcDb;
	
	private long plcToPcLiveBit;
	private long pcToPlcLiveBit;
	private boolean plcToPcLiveBitState;
	
	public String PLCName;
	public String PLCIp;
	private S7Client moka;
	private Map<Double, Boolean> boolBitChange;
	private double[] booleans;
	private Timer t = new Timer();
	
	boolean firstConnect = true;
	
	private byte[] plcToPc,pcToPlc;
	private Object plcToPcLock,pcToPlcLock;
	
	public boolean connected = false;
	public boolean liveBitEnabled = false;

	public PLC(String name,String ip,byte[] plcToPc,byte[] pcToPlc,int plcToPcDb,int pcToPlcDb,double[] booleans) {
		this.plcToPc = plcToPc;
		this.pcToPlc = pcToPlc;
		this.PLCName = name;
		this.PLCIp = ip;
		this.moka = new S7Client();
		this.moka.SetConnectionType(S7.OP);
		this.plcToPcDb = plcToPcDb;
		this.pcToPlcDb = pcToPlcDb;
		this.boolBitChange = new HashMap<Double, Boolean>();
		this.booleans = booleans;
		this.pcToPlcLock = new Object();
		this.plcToPcLock = new Object();
		this.PLCSyncObj = new Object();
		this.listeners = new ArrayList<PLCListener>();
	}
	
	public void processPLCEvents() {
		this.getBoolChange();
	}
	
	private void getBoolChange() {
		for (double b : this.booleans) {
			String s = ""+b;
			String[] nums = s.split("\\.");
			try {
				boolean state = this.getBool(true, Integer.parseInt(nums[0]), Integer.parseInt(nums[1]));
				boolean prevState;
				try {
					prevState = this.boolBitChange.get(b);
				} catch (NullPointerException e) {
					prevState = state;
				}
				if (prevState == false && state == true) {
					// Bit changed - signalize
					for(PLCListener m: this.listeners) {
						synchronized (this.PLCSyncObj) {
							m.PLCBitChanged(Integer.parseInt(nums[0]), Integer.parseInt(nums[1]), state, this.PLCName);
						}
					}
				} else if (prevState == true && state == false) {
					// Bit changed - signalize
					for(PLCListener m: this.listeners) {
						synchronized (this.PLCSyncObj) {
							m.PLCBitChanged(Integer.parseInt(nums[0]), Integer.parseInt(nums[1]), state, this.PLCName);
						}
					}
				}
			} catch (Exception e) {
				System.out.println(b);
				e.printStackTrace();
			}
		}
		this.saveBoolStatus();
	}
	
	private void saveBoolStatus() {
		for (double b : this.booleans) {
			String s = ""+b;
			String[] nums = s.split("\\.");
			try {
				this.boolBitChange.put(b,this.getBool(true, Integer.parseInt(nums[0]), Integer.parseInt(nums[1])));
			} catch (Exception e) {
			}
		}
	}
	
	public PLCStatus getStatus() {
		PLCStatus st = new PLCStatus();
		st.name = this.PLCName;
		st.ip = this.PLCIp;
		synchronized (this.plcToPcLock) {
			st.dataFromPLC = org.apache.commons.codec.binary.Base64.encodeBase64String(this.plcToPc);
		}
		synchronized (this.pcToPlcLock) {
			st.dataToPLC = org.apache.commons.codec.binary.Base64.encodeBase64String(this.pcToPlc);
		}
		return st;
	}
	
	public void refreshPLCStatus() {
		// Update incoming
		synchronized (this.plcToPcLock) {
			this.moka.ReadArea(S7.S7AreaDB, this.plcToPcDb, 0, this.plcToPc.length, this.plcToPc);
		}
		synchronized (this.pcToPlcLock) {
			this.moka.WriteArea(S7.S7AreaDB, this.pcToPlcDb, 0, this.pcToPlc.length, this.pcToPlc);
		}
		this.processPLCEvents();
	}
	
	public void inverseBit(boolean fromPLC,int address,int pos) throws Exception {
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length || pos > 7) {
					throw new Exception("PLC out of boundaries");
				} else {
					if((((byte)source[address]) & (0x01 << pos)) > 0) {
						source[address] &= ~(1 << pos);
					} else {
						source[address] |= 1 << pos;
					}
				}		
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length || pos > 7) {
					throw new Exception("PLC out of boundaries");
				} else {
					if((((byte)source[address]) & (0x01 << pos)) > 0) {
						source[address] &= ~(1 << pos);
					} else {
						source[address] |= 1 << pos;
					}
				}		
			}
		}
	}
	
	public boolean putBool(boolean fromPLC,int address,int pos,boolean val) {
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length || pos > 7) {
					System.out.println("PLC out of boundaries");
					return false;
				} else {
					if (val == true) {
						source[address] |= 1 << pos;
					} else {
						source[address] &= ~(1 << pos);
					}
				}
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length || pos > 7) {
					System.out.println("PLC out of boundaries");
					return false;
				} else {
					if (val == true) {
						source[address] |= 1 << pos;
					} else {
						source[address] &= ~(1 << pos);
					}
				}
			}
		}
		return true;
	}
	
	public void signalBoolean(boolean fromPLC,int address,int pos,boolean val) {
		try {
			this.putBool(fromPLC, address, pos, val);
			TimerTask tt = new TimerTask() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					try {
							putBool(fromPLC, address, pos, false);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			t.schedule(tt, 300);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void putIntDecimal(boolean fromPLC,int address,double val) throws Exception {
		this.putInt(fromPLC, address, (short) (val*100));
	}
	
	public boolean putInt(boolean fromPLC,int address,short val) {
		ByteBuffer b = ByteBuffer.allocate(2);
		b.putShort(val);
		byte[] spl = b.array();
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length-1) {
					System.out.println("PLC out of boundaries");
					return false;
				} else {
					source[address] = spl[0];
					source[address+1] = spl[1];
				}
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length-1) {
					System.out.println("PLC out of boundaries");
					return false;
				} else {
					source[address] = spl[0];
					source[address+1] = spl[1];
				}
			}
		}
		return true;
	}

	public void putDIntDecimal(boolean fromPLC,int address, double val) throws Exception {
		this.putDInt(fromPLC, address, (int)val*100);
	}
	
	public void putDInt(boolean fromPLC,int address,int val) throws Exception {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(val);
		byte[] spl = b.array();
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length-3) {
					throw new Exception("PLC out of boundaries");
				} else {
					source[address] = spl[0];
					source[address+1] = spl[1];
					source[address+2] = spl[2];
					source[address+3] = spl[3];
				}
			}
		} else {
			synchronized (this.plcToPcLock) {
				source = this.pcToPlc;
				if (address >= source.length-3) {
					throw new Exception("PLC out of boundaries");
				} else {
					source[address] = spl[0];
					source[address+1] = spl[1];
					source[address+2] = spl[2];
					source[address+3] = spl[3];
				}
			}
		}
	}

	public boolean getBool(boolean fromPLC,int address,int pos) throws Exception {
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length || pos > 7) {
					throw new Exception("PLC out of boundaries");
				}
				int q = ((byte)source[address]) & (0x01 << pos) ;
				if (q == 0) {
					return false;
				} else {
					return true;
				}
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length || pos > 7) {
					throw new Exception("PLC out of boundaries");
				}
				int q = ((byte)source[address]) & (0x01 << pos) ;
				if (q == 0) {
					return false;
				} else {
					return true;
				}
			}
		}
	}
	
	public int getInt(boolean fromPLC,int address) throws Exception {
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length-1) {
					throw new Exception("PLC out of boundaries");
				}
				return ((source[address] & 0xff) << 8) | (source[address+1] & 0xff);
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length-1) {
					throw new Exception("PLC out of boundaries");
				}
				return ((source[address] & 0xff) << 8) | (source[address+1] & 0xff);
			}
		}
	}

	public int getDInt(boolean fromPLC,int address) throws Exception {
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length-3) {
					throw new Exception("PLC out of boundaries");
				}
				ByteBuffer b = ByteBuffer.allocate(4);
				b.put(source, address, 4);
				b.rewind();
				return b.getInt();
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length-3) {
					throw new Exception("PLC out of boundaries");
				}
				ByteBuffer b = ByteBuffer.allocate(4);
				b.put(source, address, 4);
				b.rewind();
				return b.getInt();
			}
		}
	}

	public void checkSetLiveBit() throws Exception {
		if ((System.nanoTime() - this.pcToPlcLiveBit) > 200000000) {
			this.inverseBit(false, 0, 0);
			this.pcToPlcLiveBit = System.nanoTime();
		}
		if (this.plcToPcLiveBitState != this.getBool(true, 0, 0)) {
			// Live bit changed - reset the timer
			this.plcToPcLiveBitState = this.getBool(true, 0, 0);
			this.plcToPcLiveBit = System.nanoTime();
		}
		if ((System.nanoTime() - this.plcToPcLiveBit) > 800000000) {
			System.out.println(this.getBool(true, 0, 0));
			System.out.println(System.nanoTime() - this.plcToPcLiveBit);
			this.moka.Disconnect();
			this.moka.Connected = false;
			throw new Exception("No live bit from PLC: " + this.PLCName);
		}
	}
	
	@Override
	public void run() {
		while(true) {
			try {
				if (this.moka.Connected == false) {
					this.connected = false;
					this.moka.ConnectTo(this.PLCIp, 0, 1);
				} else {
					this.connected = true;
					if (this.firstConnect == true) {
						System.out.println("Connected to PLC: " + this.PLCIp);

						this.moka.ReadArea(S7.S7AreaDB, this.pcToPlcDb, 0, this.pcToPlc.length, this.pcToPlc);
						this.pcToPlcLiveBit = System.nanoTime();
						this.plcToPcLiveBit = System.nanoTime();
						this.plcToPcLiveBitState = this.getBool(true, 0, 0);
						this.firstConnect = false;
					}
					this.refreshPLCStatus();
					if (this.liveBitEnabled) {
						this.checkSetLiveBit();
					}
				}
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
}
