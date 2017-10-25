package si.trina.moka7.live;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sourceforge.snap7.moka7.S7;
import com.sourceforge.snap7.moka7.S7Client;

public class PLC implements Runnable {

	final Logger logger = LoggerFactory.getLogger(PLC.class);
	
	public ArrayList<PLCListener> listeners;
	public Object PLCSyncObj;

	private int plcToPcDb;
	private int pcToPlcDb;
	private int rack = 0;
	private int slot = 1;
	private int plcToPcAreaType = S7.S7AreaDB; // optionally read merker, eingang, ausgang area
	private int pcToPlcAreaType = S7.S7AreaDB;

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
	public short liveBitAddress = 0;
	public short liveBitPosition = 0;
	public short liveBitPCDuration = 250; // in ms
	public short liveBitPLCDuration = 500; // in ms
    public int LastError = 0;

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
	
	public PLC(String name,
					String ip,
					int plcToPcLength,
					int pcToPlcLength,
					int plcToPcDb,
					int pcToPlcDb,
					double[] booleans, 
					int rack, 
					int slot, 
					int plcToPcAreaType, 
					int pcToPlcAreaType) {
		this.plcToPc = new byte[plcToPcLength];
		this.pcToPlc = new byte[pcToPlcLength];
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
		this.rack = rack;
		this.slot = slot;
		this.pcToPlcAreaType = pcToPlcAreaType;
		this.plcToPcAreaType = plcToPcAreaType;
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
			this.moka.ReadArea(this.plcToPcAreaType, this.plcToPcDb, 0, this.plcToPc.length, this.plcToPc);
		}
		synchronized (this.pcToPlcLock) {
			this.moka.WriteArea(this.pcToPlcAreaType, this.pcToPlcDb, 0, this.pcToPlc.length, this.pcToPlc);
		}
		this.processPLCEvents();
	}
	
	public void inverseBit(boolean fromPLC,int address,int pos) throws Exception {
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length || pos > 7) {
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
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
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
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
					System.out.println("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
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
					System.out.println("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
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
					System.out.println("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
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
					System.out.println("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
					return false;
				} else {
					source[address] = spl[0];
					source[address+1] = spl[1];
				}
			}
		}
		return true;
	}

	public boolean putIntToByte(boolean fromPLC,int address,short val) {
		byte[] source;
		if (val > 255 || val < 0) {
			System.out.println("Value out of boundaries");
			return false;
		}
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length) {
					System.out.println("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
					return false;
				} else {
					source[address] = (byte)val;
				}
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length) {
					System.out.println("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
					return false;
				} else {
					source[address] = (byte)val;
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
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
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
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
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
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
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
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
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
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
				}
				return ((source[address] & 0xff) << 8) | (source[address+1] & 0xff);
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length-1) {
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
				}
				return ((source[address] & 0xff) << 8) | (source[address+1] & 0xff);
			}
		}
	}
	
	public int getIntFromByte(boolean fromPLC,int address) throws Exception {
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length) {
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
				}
				Byte b = new Byte(source[address]);
				return b.intValue();
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length) {
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
				}
				Byte b = new Byte(source[address]);
				return b.intValue();
			}
		}
	}

	public int getDInt(boolean fromPLC,int address) throws Exception {
		byte[] source;
		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length-3) {
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
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
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
				}
				ByteBuffer b = ByteBuffer.allocate(4);
				b.put(source, address, 4);
				b.rewind();
				return b.getInt();
			}
		}
	}
	
	public String getString(boolean fromPLC, int address, int len) throws Exception {
	    byte[] StrBuffer = new byte[len];
		byte[] source;

		if (fromPLC) {
			synchronized (this.plcToPcLock) {
				source = this.plcToPc;
				if (address >= source.length-3) {
					throw new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.plcToPcDb + " at address " + address);
				}
		        System.arraycopy(source, address, StrBuffer, 0, len);
				return S7.GetStringAt(StrBuffer, address, len);
			}
		} else {
			synchronized (this.pcToPlcLock) {
				source = this.pcToPlc;
				if (address >= source.length-3) {
					 new Exception("PLC out of boundaries: " + this.PLCName + " in DB " + this.pcToPlcDb + " at address " + address);
				}
		        System.arraycopy(source, address, StrBuffer, 0, len);
				return S7.GetStringAt(StrBuffer, address, len);
			}
		}
	}

	public void checkSetLiveBit() {
		try {
			if ((System.nanoTime() - this.pcToPlcLiveBit) > this.liveBitPCDuration * 1000000) {
				this.inverseBit(false, this.liveBitAddress, this.liveBitPosition);
				this.pcToPlcLiveBit = System.nanoTime();
			}
			if (this.plcToPcLiveBitState != this.getBool(true, this.liveBitAddress, this.liveBitPosition)) {
				// Live bit changed - reset the timer
				this.plcToPcLiveBitState = this.getBool(true, this.liveBitAddress, this.liveBitPosition);
				this.plcToPcLiveBit = System.nanoTime();
			}
			if ((System.nanoTime() - this.plcToPcLiveBit) > this.liveBitPLCDuration * 1000000) {
				System.out.println(this.getBool(true, this.liveBitAddress, this.liveBitPosition));
				System.out.println(System.nanoTime() - this.plcToPcLiveBit);
				this.moka.Disconnect();
				this.moka.Connected = false;
				logger.error("No live bit from PLC: " + this.PLCName);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		while(true) {
			if (this.moka.Connected == false) {
				this.connected = false;
				int error = this.moka.ConnectTo(this.PLCIp, this.rack, this.slot);
				if (error > 0) {
					logger.error(S7Client.ErrorText(error));
				}
			} else {
				this.connected = true;
				this.LastError = this.moka.LastError;
				
				if (this.firstConnect == true) {
					logger.info("Connected to PLC " + this.PLCName);
					// read current db state, so we don't override it with zeroes
					this.moka.ReadArea(this.pcToPlcAreaType, this.pcToPlcDb, 0, this.pcToPlc.length, this.pcToPlc);
					this.firstConnect = false;
					if (this.liveBitEnabled) {
						this.pcToPlcLiveBit = System.nanoTime();
						this.plcToPcLiveBit = System.nanoTime();
						try {
							this.plcToPcLiveBitState = this.getBool(true, this.liveBitAddress, this.liveBitPosition);
						} catch (Exception e) {
							logger.error(e.getMessage());
						}
					}
				}
				
				this.refreshPLCStatus();
				if (this.liveBitEnabled) {
					this.checkSetLiveBit();
				}
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
			}
		}
	}
	
}
