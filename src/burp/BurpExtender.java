package burp;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import com.josh.ActionJackson;

public class BurpExtender implements IBurpExtender,IContextMenuFactory{
	private IBurpExtenderCallbacks cb;
	private HashMap<String,String> sploits = new HashMap<String,String>();
	private HashMap<String,String> remotes = new HashMap<String,String>();
	

	@Override
	public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
		
		this.cb = callbacks;
		cb.setExtensionName("sploits");
		cb.registerContextMenuFactory(this);
		propsTest();
		getSploits();
		
		
	}

	@Override
	public List<JMenuItem> createMenuItems(IContextMenuInvocation inv) {
		
		JMenuItem inc = new JMenuItem("Add All To Scope");
		inc.addActionListener(new ActionJackson(inv, cb, false)); // This will add the domain to the scope.
		JMenuItem exc = new JMenuItem("Remove All From Scope");
		exc.addActionListener(new ActionJackson(inv, cb, true)); // This will add the domain to the scope.
		
		List<JMenuItem>stuff = new ArrayList<JMenuItem>();
		stuff.add(inc);
		stuff.add(exc);
		
		if(isRequest(inv) || isResponse(inv)){
			JMenu sub = new JMenu("sploits");
			sub.setMnemonic(KeyEvent.VK_S);
			//sub.setForeground(Color.red);
			//sub.setFont(new Font("courier", Font.PLAIN, 24));
			JMenu config = new JMenu("sploits config");
			//Convert to treemap to automatically sort results before displaying in the menu
			Map<String, String> sorted = new TreeMap<String, String>(sploits);
			//Remote Menu Collections
			HashMap<String,TreeMap<String,Object>> remoteSubMenues = new HashMap<String,TreeMap<String,Object>>();
			//Normal Sub Menu collections
			HashMap<String,JMenu> addedSubMenues = new HashMap<String,JMenu>();
			int cmdNum = 1;
			for(String sploitKey : sorted.keySet()){
				
				if(sploitKey.startsWith("r_")){  // THis is a remote repo.. needs its own menu
					String title = sploitKey.substring(2, sploitKey.indexOf("_", 2));
					if(!remoteSubMenues.containsKey(title)){
						remoteSubMenues.put(title, new TreeMap<String, Object>());
					}
					String subtitle = sploitKey.replace("r_"+title+"_", "");
					if(!subtitle.contains(".")){
						JMenuItem jmi = new JMenuItem(subtitle);
						jmi.setToolTipText(sploits.get(sploitKey));
						//jmi.setFont(new Font("courier", Font.PLAIN, 24));
						jmi.addActionListener(new ActionJackson(inv, cb, sploits, sploitKey));
						remoteSubMenues.get(title).put(subtitle, jmi);
					}else{
						String subsubmenu = subtitle.split("\\.")[0];
						
						if(!remoteSubMenues.get(title).containsKey(subsubmenu)){
							remoteSubMenues.get(title).put(subsubmenu, new TreeMap<String, JMenuItem>());
						}
						//OMG where ami?!?!... this should have been a recursive function
						JMenuItem jmi = new JMenuItem(subtitle.split("\\.")[1]);
						jmi.setToolTipText(sploits.get(sploitKey));
						//jmi.setFont(new Font("courier", Font.PLAIN, 24));
						jmi.addActionListener(new ActionJackson(inv, cb, sploits, sploitKey));
						((TreeMap<String, JMenuItem>)remoteSubMenues.get(title).get(subsubmenu)).put(subtitle.split("\\.")[1], jmi);
						
					}
					
				}else{ // These are local user sploits
					if(sploitKey.contains(".")){ // these items have a sub menu
						String subkey = sploitKey.split("\\.")[0];
						if(!addedSubMenues.containsKey(subkey)){
							JMenu submenu = new JMenu(subkey);
							//submenu.setFont(new Font("courier", Font.PLAIN, 24));
							addedSubMenues.put(subkey, new JMenu(subkey));
						}
						JMenuItem subsub = new JMenuItem(sploitKey.split("\\.")[1]);
						subsub.setToolTipText(sploits.get(sploitKey));
						//subsub.setFont(new Font("courier", Font.PLAIN, 24));
						subsub.addActionListener(new ActionJackson(inv, cb, sploits, sploitKey));
						addedSubMenues.get(subkey).add(subsub);
					}else{  // these are normal items
						JMenuItem spm = new JMenuItem(sploitKey);
						if(cmdNum <=9)
							spm.setAccelerator(KeyStroke.getKeyStroke("shift " + cmdNum));
						spm.setToolTipText(sploits.get(sploitKey));
						//spm.setFont(new Font("courier", Font.PLAIN, 24));
						spm.addActionListener(new ActionJackson(inv, cb, sploits, sploitKey));
						sub.add(spm);
					}
				}
				
			}
			// Add User submenues
			for(String skey :addedSubMenues.keySet()){
				//addedSubMenues.get(skey).setFont(new Font("courier", Font.PLAIN, 24));
				sub.add(addedSubMenues.get(skey));
			}
			
			//add remote submenues
			for(String rkey : remoteSubMenues.keySet()){
				TreeMap<String,Object> hms = remoteSubMenues.get(rkey);
				JMenu remote = new JMenu(rkey);
				//remote.setFont(new Font("courier", Font.PLAIN, 24));
				for(String rrkey : hms.keySet()){
					if(hms.get(rrkey).getClass().getName().contains("JMenuItem")){
						remote.add((JMenuItem)hms.get(rrkey));
					}else{ // we have a treemap instead
						JMenu remoteMenu = new JMenu(rrkey);
						//remoteMenu.setFont(new Font("courier", Font.PLAIN, 24));
						TreeMap<String, JMenuItem> items = (TreeMap<String, JMenuItem>)hms.get(rrkey);
						for(String item : items.keySet()){
							remoteMenu.add(items.get(item));
						}
						remote.add(remoteMenu);
					}
				}
				sub.add(remote);
				
			}
			// Adding Config Options
			//This will update the submenu items both locally and from remote lists
			JMenuItem update = new JMenuItem("Refresh sploits");
			update.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					getSploits();	
				}
				
			});
			config.add(update);
			
			// This allows us to select text and add it to the user's local list of sploits
			JMenuItem add = new JMenuItem("Add To sploits");
			add.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
						add2sploits(inv);
				}
				
			});
			config.add(add);
			
			// We need this to access remote lists from behind a corp firewall
			JMenuItem proxy = new JMenuItem("Add Proxy");
			proxy.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
						addPoxy();
				}
				
			});
			config.add(proxy);
			
			//Add External sploit lists from URL's
			JMenuItem remote = new JMenuItem("Add Remote sploits");
			remote.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
						addRemote();
				}
				
			});
			config.add(remote);
			
			// Delete URL's of exteneral sploits
			JMenuItem dremote = new JMenuItem("Delete Remote sploits");
			dremote.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
						delRemote();
				}
				
			});
			config.add(dremote);
			
			// Delete a local sploit
			JMenuItem delete = new JMenuItem("Delete sploit");
			delete.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
						deleteSploit();
				}
				
			});
			config.add(delete);
			
			// Copy all remote sploits to the internal properties file
			// This will also remove remote URL.
			JMenuItem convert = new JMenuItem("Convert Remote to Local");
			convert.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					convertRemote2local();
				}
				
			});
			config.add(convert);
			
			stuff.add(sub);
			stuff.add(config);
		}
		
		
		return stuff;
	}

	
	private boolean isRequest(IContextMenuInvocation inv){
		
		if(inv.getInvocationContext() == inv.CONTEXT_MESSAGE_EDITOR_REQUEST || inv.getInvocationContext() == inv.CONTEXT_MESSAGE_VIEWER_REQUEST)
			return true;
		else 
			return false;
		
	}
	private boolean isResponse(IContextMenuInvocation inv){
		if(inv.getInvocationContext() == inv.CONTEXT_MESSAGE_EDITOR_RESPONSE || inv.getInvocationContext() == inv.CONTEXT_MESSAGE_EDITOR_RESPONSE)
			return true;
		else 
			return false;
		
	}
	
	/************************************************************************************
	 * Below this line are all the supporting functions for managing your sploit libraries
	 * 
	 */
	
	private void deleteSploit(){
		List<String> list =  new ArrayList<String>();
		for(String key : sploits.keySet()){
			if(!key.startsWith("r_"))
				list.add(""+key + "=" + sploits.get(key) );
		}
		
		String dsploit = (String)JOptionPane.showInputDialog(
                new JFrame(),
                "Delete a local sploit: ",
                "Delete sploits",
                JOptionPane.PLAIN_MESSAGE,
                null,
                list.toArray(),
                null);
		if(dsploit!=null && !dsploit.equals("")){
			delProps(dsploit.split("=")[0]);
			getSploits();
		}
	}
	
	private void delRemote(){
		List<String> list =  new ArrayList<String>();
		for(String key : remotes.keySet()){
			list.add(""+key + "=" + remotes.get(key) );
		}
		
		String remote = (String)JOptionPane.showInputDialog(
                new JFrame(),
                "Delete a remote host: ",
                "Update sploits",
                JOptionPane.PLAIN_MESSAGE,
                null,
                list.toArray(),
                null);
		if(remote!=null && !remote.equals("")){
			String rkey = remote.split("=")[0];
			delProps(rkey);
			getSploits();
		}
		
		
	}
	
	private void addPoxy(){
		String proxy = (String)JOptionPane.showInputDialog(
                new JFrame(),
                "Enter proxy (hostname:port): ",
                "Update sploits",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                null);
		if(proxy.contains(":")){
			add2props("proxy", proxy);
			getSploits();
		}else{
			
		}
	}
	
	private void addRemote(){
		String remote = (String)JOptionPane.showInputDialog(
                new JFrame(),
                "Enter remote URL: ",
                "Update sploits",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                null);
		add2props("remote" + remotes.size(), remote);
		getSploits();
	}
	
	private void add2sploits(IContextMenuInvocation inv){
		
		int start = inv.getSelectionBounds()[0];
		int stop = inv.getSelectionBounds()[1];
		for(IHttpRequestResponse o : inv.getSelectedMessages()){
			String all = (new String(isRequest(inv)? o.getRequest(): o.getResponse()));
			String Selected = all.substring(start, stop);
			String Key = (String)JOptionPane.showInputDialog(
                    new JFrame(),
                    "Enter sploit Name: ",
                    "Update sploits",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    null);
			add2props(Key, Selected);
			getSploits();
		}
		
	}
	
	private void getURLSploits(String URL, String proxy){
		
		Properties prop = new Properties();
		try {
			URL url = new URL(URL);
			URLConnection conn = null;
		
			if(proxy != null && !proxy.equals("")){
				String [] splits = proxy.split(":");
				Proxy prox = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(splits[0], Integer.parseInt(splits[1])));
				conn = url.openConnection(prox);
				
			}else{
				conn = url.openConnection();
			}
			
			InputStream in = conn.getInputStream();
			Reader reader = new InputStreamReader(in, "UTF-8"); // for example
			// load a properties file
			prop.load(reader);
			String title = prop.getProperty("title","");
			if(!title.equals(""))
				title+="_";
			for(Object key : prop.keySet()){
				if(!key.equals("title"))
					sploits.put("r_"+title +key, prop.getProperty(""+key));
				
			}
			
			

		} catch (IOException ex) {
			ex.printStackTrace();
			
		}
		
	}
	
	private void convertRemote2local(){
		int isYes = (int)JOptionPane.showOptionDialog(
                new JFrame(),
                "Do You Want to Convert All Remote sploits to Local sploits? \n"
                + "This will also delete all remote urls.",
                "Update sploits",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,null,null);
		if(isYes == 0){
			for(String key : sploits.keySet()){
				if(key.startsWith("r_")){ // r_'s are imported from external sources
					String newKey = key.replaceFirst("r_", "");
					add2props(newKey, sploits.get(key));
				}
			}
			for(String key : remotes.keySet()){
				delProps(key);
			}
			getSploits();
		}
	}
	/************************************************************************************
	 * properties file management
	 * 
	 */
	
	private void add2props(String key, String value){
		Properties prop = new Properties();
		InputStream input = null;
		FileOutputStream fos = null;
		try {
			
			input = new FileInputStream("sploits.properties");
			
			// load a properties file
			prop.load(input);
			prop.setProperty(key, value);
			fos = new FileOutputStream("sploits.properties");
			prop.store(fos, "####Updated with sploits ");
			

		} catch (IOException ex) {
			ex.printStackTrace();
			
		} finally {
			if (input != null) {
				try {
					input.close();
					fos.close();
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	private void delProps(String key){
		Properties prop = new Properties();
		InputStream input = null;
		FileOutputStream fos = null;
		try {
			
			input = new FileInputStream("sploits.properties");
			
			// load a properties file
			prop.load(input);
			prop.remove(key);
			fos = new FileOutputStream("sploits.properties");
			prop.store(fos, "####Updated with sploits ");
			

		} catch (IOException ex) {
			ex.printStackTrace();
			
		} finally {
			if (input != null) {
				try {
					input.close();
					fos.close();
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	private void getSploits(){
		sploits = new HashMap<String,String>();
		remotes = new HashMap<String,String>();
		Properties prop = new Properties();
		InputStream input = null;
		try {
			
			input = new FileInputStream("sploits.properties");
			// load a properties file
			prop.load(input);
			for(Object key : prop.keySet()){
				if(((String) key).startsWith("remote")){
					getURLSploits(prop.getProperty(""+key),prop.getProperty("proxy",""));
					remotes.put(""+key, prop.getProperty(""+key));
				}else if (!key.equals("proxy")){
					sploits.put(""+key, prop.getProperty(""+key));
				}
				
			}
			

		} catch (IOException ex) {
			ex.printStackTrace();
			
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private void propsTest(){
		File prop = new File("sploits.properties");
		if(!prop.exists()){
			try {
				prop.createNewFile();
				add2props("remote0", "https://raw.githubusercontent.com/summitt/sploits-default/master/sploits.properties");
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
	}
	
	



}
