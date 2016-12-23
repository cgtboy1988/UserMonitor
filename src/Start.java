

import java.awt.Image;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.NativeInputEvent;
//import org.jnativehook.dispatcher.SwingDispatchService;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;
import org.jnativehook.mouse.NativeMouseEvent;
import org.jnativehook.mouse.NativeMouseInputListener;
import org.jnativehook.mouse.NativeMouseWheelEvent;
import org.jnativehook.mouse.NativeMouseWheelListener;



public class Start implements NativeMouseInputListener, Runnable, ScreenshotListener
{
	private Thread myThread;
	private ActiveWindowMonitor myMonitor = new ActiveWindowMonitor();
	private String windowID = "";
	private String windowName = "";
	private int windowX = -1;
	private int windowY = -1;
	private int windowWidth = -1;
	private int windowHeight = -1;
	private HashMap currentWindowData = null;
	private ConcurrentLinkedQueue windowsToWrite = new ConcurrentLinkedQueue();
	private ConcurrentLinkedQueue clicksToWrite = new ConcurrentLinkedQueue();
	private ConcurrentLinkedQueue screenshotsToWrite = new ConcurrentLinkedQueue();
	private int screenshotTimeout = 15000;
	private ScreenshotGenerator myGenerator;
	
	private String userName = "user7";
	
	public Start()
	{
		myThread = new Thread(this);
		myThread.start();
		try
		{
			Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
			logger.setLevel(Level.OFF);
			GlobalScreen.registerNativeHook();
			GlobalScreen.addNativeMouseListener(this);
		}
		catch(NativeHookException e)
		{
			e.printStackTrace();
		}
		myGenerator = new ScreenshotGenerator(screenshotTimeout);
		myGenerator.addScreenshotListener(this);
		
	}

	public static void main(String[] args)
	{
		Start myStart = new Start();
	}
	
	public synchronized boolean checkNew(HashMap newWindow)
	{
		if(newWindow == null)
		{
			return false;
		}
		if(!newWindow.get("WindowID").equals(windowID) || !newWindow.get("WindowTitle").equals(windowName) || !((int)newWindow.get("x") == windowX) || !((int)newWindow.get("y") == windowY || !((int)newWindow.get("width") == windowWidth) || !((int)newWindow.get("height") == windowHeight)))
		{
			windowName = (String) newWindow.get("WindowTitle");
			windowID = (String) newWindow.get("WindowID");
			windowX = (int) newWindow.get("x");
			windowY = (int) newWindow.get("y");
			windowWidth = (int) newWindow.get("width");
			windowHeight = (int) newWindow.get("height");
			if(newWindow != null)
			{
				//Calendar currentTime = Calendar.getInstance();
				newWindow.put("clickedInTime", new Timestamp(new Date().getTime()));
				windowsToWrite.add(newWindow);
			}
			currentWindowData = newWindow;
			System.out.println("New window");
			return true;
		}
		return false;
	}

	
	boolean recordClick = false;
	@Override
	public void nativeMouseClicked(NativeMouseEvent arg0)
	{
		if(!recordClick)
		{
			return;
		}
		//System.out.println("Mouse Clicked: " + arg0.getX() + ", " + arg0.getY());
		checkNew(myMonitor.getTopWindow());
		HashMap clickToWrite = new HashMap();
		clickToWrite.put("xLoc", arg0.getX());
		clickToWrite.put("yLoc", arg0.getY());
		clickToWrite.put("type", "click");
		//Calendar currentTime = Calendar.getInstance();
		clickToWrite.put("clickTime", new Timestamp(new Date().getTime()));
		clickToWrite.put("window", currentWindowData);
		clicksToWrite.add(clickToWrite);
	}

	@Override
	public void nativeMousePressed(NativeMouseEvent arg0)
	{
		//System.out.println("Mouse Pressed: " + arg0.getX() + ", " + arg0.getY());
		checkNew(myMonitor.getTopWindow());
		HashMap clickToWrite = new HashMap();
		clickToWrite.put("xLoc", arg0.getX());
		clickToWrite.put("yLoc", arg0.getY());
		clickToWrite.put("type", "down");
		//Calendar currentTime = Calendar.getInstance();
		clickToWrite.put("clickTime", new Timestamp(new Date().getTime()));
		clickToWrite.put("window", currentWindowData);
		clicksToWrite.add(clickToWrite);
	}

	@Override
	public void nativeMouseReleased(NativeMouseEvent arg0)
	{
		//System.out.println("Mouse Released: " + arg0.getX() + ", " + arg0.getY());
		checkNew(myMonitor.getTopWindow());
		HashMap clickToWrite = new HashMap();
		clickToWrite.put("xLoc", arg0.getX());
		clickToWrite.put("yLoc", arg0.getY());
		clickToWrite.put("type", "up");
		//Calendar currentTime = Calendar.getInstance();
		clickToWrite.put("clickTime", new Timestamp(new Date().getTime()));
		clickToWrite.put("window", currentWindowData);
		clicksToWrite.add(clickToWrite);
	}

	
	private boolean useDragged = false;
	@Override
	public void nativeMouseDragged(NativeMouseEvent arg0)
	{
		if(!useDragged)
		{
			return;
		}
		//System.out.println("Mouse Dragged: " + arg0.getX() + ", " + arg0.getY());
		checkNew(myMonitor.getTopWindow());
		HashMap clickToWrite = new HashMap();
		clickToWrite.put("xLoc", arg0.getX());
		clickToWrite.put("yLoc", arg0.getY());
		clickToWrite.put("type", "drag");
		//Calendar currentTime = Calendar.getInstance();
		clickToWrite.put("clickTime", new Timestamp(new Date().getTime()));
		clickToWrite.put("window", currentWindowData);
		clicksToWrite.add(clickToWrite);
	}

	@Override
	public void nativeMouseMoved(NativeMouseEvent arg0)
	{
		//System.out.println("Mouse Moved: " + arg0.getX() + ", " + arg0.getY());
		
	}

	@Override
	public void run()
	{
		TestingConnectionSource connectionSource = new TestingConnectionSource();
		Connection myConnection = connectionSource.getDatabaseConnection();
		int count = 0;
		while(true)
		{
			count++;
			try
			{
				if(myConnection.isClosed())
				{
					myConnection = connectionSource.getDatabaseConnection();
				}
			}
			catch(SQLException e1)
			{
				
			}
			
			
			try
			{
				checkNew(myMonitor.getTopWindow());
				if(count > 5 && !windowsToWrite.isEmpty() || !clicksToWrite.isEmpty() || !screenshotsToWrite.isEmpty())
				{
					myConnection.setAutoCommit(false);
					
					System.out.println("Recording user JIC");
					
					String userInsert = "INSERT IGNORE INTO `dataCollection`.`User` (`username`) VALUES ";
					String userRow = "(?)";
					userInsert += userRow;
					PreparedStatement userStatement = myConnection.prepareStatement(userInsert);
					userStatement.setString(1, userName);
					userStatement.execute();
					
					
					System.out.println("Time to record " + windowsToWrite.size() + " window changes");
					
					int toInsert = windowsToWrite.size();
					int clickToInsert = clicksToWrite.size();
					int screenshotsToInsert = screenshotsToWrite.size();
					ConcurrentLinkedQueue countQueue = new ConcurrentLinkedQueue();
					//ConcurrentLinkedQueue nextQueue = new ConcurrentLinkedQueue();
					
					if(toInsert > 0)
					{
						String processInsert = "INSERT IGNORE INTO `dataCollection`.`Process` (`username`, `user`, `pid`, `start`, `command`) VALUES ";
						String eachProcessRow = "(?, ?, ?, ?, ?)";
						
						String processArgInsert = "INSERT IGNORE INTO `dataCollection`.`ProcessArgs` (`username`, `user`, `pid`, `start`, `numbered`, `arg`) VALUES ";
						String eachProcessArgRow = "(?, ?, ?, ?, ?, ?)";
						//int argCount = 0;
						
						String processAttInsert = "INSERT IGNORE INTO `dataCollection`.`ProcessAttributes` (`username`, `user`, `pid`, `start`, `cpu`, `mem`, `vsz`, `rss`, `tty`, `stat`, `time`, `timestamp`) VALUES ";
						String eachProcessAttRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						String windowInsert = "INSERT IGNORE INTO `dataCollection`.`Window` (`username`, `user`, `pid`, `start`, `xid`, `firstClass`, `secondClass`) VALUES ";
						String eachWindowRow = "(?, ?, ?, ?, ?, ?, ?)";
						
						String windowDetailInsert = "INSERT IGNORE INTO `dataCollection`.`WindowDetails` (`username`, `user`, `pid`, `start`, `xid`, `x`, `y`, `width`, `height`, `name`, `timeChanged`) VALUES ";
						String eachWindowDetailRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						boolean hasArgs = false;
						
						for(int x=0; x<toInsert; x++)
						{
							HashMap tmpMap = (HashMap) windowsToWrite.poll();
							HashMap tmpProcess = (HashMap) tmpMap.get("ProcessInfo");
							ArrayList argList = (ArrayList) tmpProcess.get("ARGS");
							//argCount += argList.size();
							
							for(int y=0; argList != null && y<argList.size(); y++)
							{
								hasArgs = true;
								if(y > 0)
								{
									processArgInsert += ", " + eachProcessArgRow;
								}
								else
								{
									processArgInsert += eachProcessArgRow;
								}
							}
							
							//System.out.println(tmpProcess);
							
							countQueue.add(tmpMap);
							
							if(x > 0)
							{
								processInsert += ", " + eachProcessRow;
								processAttInsert += ", " + eachProcessAttRow;
								windowInsert += ", " + eachWindowRow;
								windowDetailInsert += ", " + eachWindowDetailRow;
							}
							else
							{
								processInsert += eachProcessRow;
								processAttInsert += eachProcessAttRow;
								windowInsert += eachWindowRow;
								windowDetailInsert += eachWindowDetailRow;
							}
						}
						
						System.out.println(processInsert);
						System.out.println(processAttInsert);
						System.out.println(windowInsert);
						System.out.println(windowDetailInsert);
						System.out.println(processArgInsert);
						
						PreparedStatement processStatement = myConnection.prepareStatement(processInsert);
						PreparedStatement processAttStatement = myConnection.prepareStatement(processAttInsert);
						PreparedStatement windowStatement = myConnection.prepareStatement(windowInsert);
						PreparedStatement windowDetailStatement = myConnection.prepareStatement(windowDetailInsert);
						
						//System.out.println(processArgInsert);
						PreparedStatement processArgStatement = null;
						if(hasArgs)
						{
							processArgStatement = myConnection.prepareStatement(processArgInsert);
						}
						
						int fieldCount = 1;
						int argFieldCount = 1;
						int attFieldCount = 1;
						int windowFieldCount = 1;
						int windowDetailFieldCount = 1;
						for(int x=0; x<toInsert; x++)
						{
							HashMap tmpMap = (HashMap) countQueue.poll();
							
							HashMap tmpProcess = (HashMap) tmpMap.get("ProcessInfo");
							
							processStatement.setString(fieldCount, userName);
							processAttStatement.setString(attFieldCount, userName);
							windowStatement.setString(windowFieldCount, userName);
							windowDetailStatement.setString(windowDetailFieldCount, userName);
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, (String) tmpProcess.get("USER"));
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("USER"));
							windowStatement.setString(windowFieldCount, (String) tmpProcess.get("USER"));
							windowDetailStatement.setString(windowDetailFieldCount, (String) tmpProcess.get("USER"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, (String) tmpProcess.get("PID"));
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("PID"));
							windowStatement.setString(windowFieldCount, (String) tmpProcess.get("PID"));
							windowDetailStatement.setString(windowDetailFieldCount, (String) tmpProcess.get("PID"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, (String) tmpProcess.get("START"));
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("START"));
							windowStatement.setString(windowFieldCount, (String) tmpProcess.get("START"));
							windowDetailStatement.setString(windowDetailFieldCount, (String) tmpProcess.get("START"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							//processStatement.setString(fieldCount, (String) tmpProcess.get("TIME"));
							//fieldCount++;
							
							processStatement.setString(fieldCount, (String) tmpProcess.get("COMMAND"));
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("%CPU"));
							windowStatement.setString(windowFieldCount, (String) tmpMap.get("WindowID"));
							windowDetailStatement.setString(windowDetailFieldCount, (String) tmpMap.get("WindowID"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("%MEM"));
							windowStatement.setString(windowFieldCount, (String) tmpMap.get("WindowFirstClass"));
							windowDetailStatement.setInt(windowDetailFieldCount, (int) tmpMap.get("x"));
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("VSZ"));
							windowStatement.setString(windowFieldCount, (String) tmpMap.get("WindowSecondClass"));
							windowDetailStatement.setInt(windowDetailFieldCount, (int) tmpMap.get("y"));
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("RSS"));
							windowDetailStatement.setInt(windowDetailFieldCount, (int) tmpMap.get("width"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("TTY"));
							windowDetailStatement.setInt(windowDetailFieldCount, (int) tmpMap.get("height"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("STAT"));
							windowDetailStatement.setString(windowDetailFieldCount, (String) tmpMap.get("WindowTitle"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, (String) tmpProcess.get("TIME"));
							windowDetailStatement.setTimestamp(windowDetailFieldCount, (Timestamp) tmpMap.get("clickedInTime"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setTimestamp(attFieldCount, (Timestamp) tmpMap.get("clickedInTime"));
							attFieldCount++;
							
							ArrayList argList = (ArrayList) tmpProcess.get("ARGS");
							for(int y=0; argList != null && y<argList.size(); y++)
							{
								processArgStatement.setString(argFieldCount, userName);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, (String) tmpProcess.get("USER"));
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, (String) tmpProcess.get("PID"));
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, (String) tmpProcess.get("START"));
								argFieldCount++;
								
								//processArgStatement.setString(argFieldCount, (String) tmpProcess.get("TIME"));
								//argFieldCount++;
								
								processArgStatement.setInt(argFieldCount, y);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, (String) argList.get(y));
								argFieldCount++;
							}
							
							//System.out.println(tmpProcess);
							
							//nextQueue.add(tmpMap);
						}
						
						processStatement.execute();
						processAttStatement.execute();
						windowStatement.execute();
						windowDetailStatement.execute();
						if(hasArgs)
						{
							processArgStatement.execute();
						}
						
					}
					
					//toWrite.clear();
					
					System.out.println("Time to record " + clicksToWrite.size() + " mouse clicks");
					if(clickToInsert > 0)
					{
						ConcurrentLinkedQueue nextClickQueue = new ConcurrentLinkedQueue();
						
						String mouseClickInsert = "INSERT IGNORE INTO `dataCollection`.`MouseInput` (`username`, `user`, `pid`, `start`, `xid`, `timeChanged`, `type`, `xLoc`, `yLoc`, `inputTime`) VALUES ";
						String mouseClickRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						for(int x=0; x < clickToInsert; x++)
						{
							HashMap clickMap = (HashMap) clicksToWrite.poll();
							HashMap windowMap = (HashMap) clickMap.get("window");
							if(windowMap == null)
							{
								clickToInsert--;
								//continue;
							}
							else
							{
								nextClickQueue.add(clickMap);
								if(x==0)
								{
									mouseClickInsert += mouseClickRow;
								}
								else
								{
									mouseClickInsert += ", " + mouseClickRow;
								}
							}
						}
						
						System.out.println(mouseClickInsert);
						
						PreparedStatement mouseClickStatement = myConnection.prepareStatement(mouseClickInsert);
						
						int mouseClickCount = 1;
						
						
						
						for(int x=0; clickToInsert > 0 && x < clickToInsert; x++)
						{
							HashMap clickMap = (HashMap) nextClickQueue.poll();
							HashMap tmpMap = (HashMap) clickMap.get("window");
							HashMap tmpProcess = (HashMap) tmpMap.get("ProcessInfo");
							
							mouseClickStatement.setString(mouseClickCount, userName);
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, (String) tmpProcess.get("USER"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, (String) tmpProcess.get("PID"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, (String) tmpProcess.get("START"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, (String) tmpMap.get("WindowID"));
							mouseClickCount++;
							
							//System.out.println(tmpMap.get("clickedInTime"));
							mouseClickStatement.setTimestamp(mouseClickCount, (Timestamp) tmpMap.get("clickedInTime"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, (String) clickMap.get("type"));
							mouseClickCount++;
							
							mouseClickStatement.setInt(mouseClickCount, (int) clickMap.get("xLoc"));
							mouseClickCount++;
							
							mouseClickStatement.setInt(mouseClickCount, (int) clickMap.get("yLoc"));
							mouseClickCount++;
							
							//System.out.println(clickMap.get("clickTime"));
							mouseClickStatement.setTimestamp(mouseClickCount, (Timestamp) clickMap.get("clickTime"));
							mouseClickCount++;
							
						}
						
						if(clickToInsert > 0)
						{
							mouseClickStatement.execute();
						}
					}
					
					
					System.out.println("Time to record " + screenshotsToWrite.size() + " screenshots");
					if(screenshotsToInsert > 0)
					{
						ConcurrentLinkedQueue nextScreenshotQueue = new ConcurrentLinkedQueue();
						
						String screenshotInsert = "INSERT IGNORE INTO `dataCollection`.`Screenshot` (`username`, `taken`, `screenshot`) VALUES ";
						String screenshotRow = "(?, ?, ?)";
						
						for(int x=0; x < screenshotsToInsert; x++)
						{
							Object[] screenshotEntry = (Object[]) screenshotsToWrite.poll();
							if(screenshotEntry == null)
							{
								screenshotsToInsert--;
								//continue;
							}
							else
							{
								nextScreenshotQueue.add(screenshotEntry);
								if(x==0)
								{
									screenshotInsert += screenshotRow;
								}
								else
								{
									screenshotInsert += ", " + screenshotRow;
								}
							}
						}
						
						System.out.println(screenshotInsert);
						
						PreparedStatement screenshotStatement = myConnection.prepareStatement(screenshotInsert);
						
						int screenshotCount = 1;
						
						
						
						for(int x=0; screenshotsToInsert > 0 && x < screenshotsToInsert; x++)
						{
							Object[] clickMap = (Object[]) nextScreenshotQueue.poll();
							
							screenshotStatement.setString(screenshotCount, userName);
							screenshotCount++;
							
							screenshotStatement.setTimestamp(screenshotCount, (Timestamp) clickMap[0]);
							screenshotCount++;
							
							screenshotStatement.setBytes(screenshotCount, (byte[]) clickMap[1]);
							screenshotCount++;
							
							
						}
						
						if(screenshotsToInsert > 0)
						{
							screenshotStatement.execute();
						}
					}
					
					//clicksToWrite.clear();
					count = 0;
					
					myConnection.commit();
				}
				Thread.sleep(500);
				//System.out.println("Loop");
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		
	}

	@Override
	public void getScreenshotEvent(Date timeTaken, Image screenshot)
	{
		Object[] myPair = new Object[2];
		
		try
		{
			JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
			jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			jpegParams.setCompressionQuality((float) .25);
			ByteArrayOutputStream toByte = new ByteArrayOutputStream();
			ImageOutputStream imageOutput = ImageIO.createImageOutputStream(toByte);
			ImageWriter myWriter = ImageIO.getImageWritersByFormatName("jpg").next();
			myWriter.setOutput(imageOutput);
			
			
			myWriter.write(null, new IIOImage((RenderedImage) screenshot, null, null), jpegParams);
			
			
			myPair[0] = new Timestamp(timeTaken.getTime());
			myPair[1] = toByte.toByteArray();
			screenshotsToWrite.add(myPair);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

}
