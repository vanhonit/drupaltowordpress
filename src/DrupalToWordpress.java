/*
 *
 *
 * Simple Drupal 6 to Wordpress 3 migrating class for the modeling-languages.com portal
 *
 *
 * @version 0.1 10 June 2011
 * @author Jordi Cabot
 *
 *
 * Software licensed under Creative Commons Attribution Non-Commercial 3.0 License
 *
 * If you are not familiar with this license please read the full details here: http://creativecommons.org/licenses/by-nc/3.0/
 *
 *
 */



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
public class DrupalToWordpress {


	/**
	 * @param args
	 */
	public static String wpPrefix;
	public static String drPrefix;
	public static String dbName;
	public static String dbUserName;
	public static String dbPassword;
	public static String host;
	public static String dir;
	public static String ngg_dir;
	public static Connection dbConnect;
	public static void main(String[] args)
	{
	  try
	  {
		/*String wpPrefix="wp_";
		String drPrefix="";
		dir = "/home/honnguyen/public_html/perspektive89/wp-content/uploads/images";
		ngg_dir = "/home/honnguyen/public_html/perspektive89/wp-content/gallery/image";*/
		readConfig();
		dbConnect = getConnection();

		//Deleting old data
		deleteWPData(dbConnect,wpPrefix);
		System.out.println("Data cleaning finished");

		//Creating the category taxonomy
		createCategories(dbConnect,wpPrefix,drPrefix);
		System.out.println("====================== Categories created ====================");
		
		//Creating tags
		
		createTags();
		createImagesTags();
		System.out.println("====================== Tags created ====================");

		//Creating the posts
		createPosts(dbConnect,wpPrefix,drPrefix);
		System.out.println("====================== Posts created ====================");

		//Creating the comments
		//createComments(dbConnect,wpPrefix,drPrefix);
		System.out.println("====================== Comments created ====================");
		createUser(dbConnect, wpPrefix, drPrefix);
		//createFile(dbConnect, wpPrefix, drPrefix);
		//createGallery(dbConnect, wpPrefix, drPrefix);
		//moveFiles();	  
		//Connection to the database (we assume both drupal and wordpress tables are in the same database)
	  } catch (Exception e) {System.err.println(e.getClass()+ "  " + e.getMessage());System.exit(-1);}
	}

    //Establishing the connection with the database
	static Connection getConnection() throws Exception
	{
    	// Loading the MySQL driver
	    Class.forName("com.mysql.jdbc.Driver");
	    return DriverManager.getConnection("jdbc:mysql://"+ host +"/" + dbName + "?" + "user=" + dbUserName +"&password="+ dbPassword);
   	}
	static void readConfig() throws Exception{
		JSONParser parser = new JSONParser();
		try {

	        Object obj = parser.parse(new FileReader("config.json"));

	        JSONObject jsonObject =  (JSONObject) obj;

	        wpPrefix = (String) jsonObject.get("wpPrefix");
	        drPrefix = (String) jsonObject.get("drPrefix");	        
	        dbName = (String) jsonObject.get("dbName");	        
	        dbPassword = (String) jsonObject.get("dbPassword");	        
	        dbUserName = (String) jsonObject.get("dbUsername");	        
	        host = (String) jsonObject.get("host");	        
	        dir = (String) jsonObject.get("copyFrom");	        
	        ngg_dir = (String) jsonObject.get("copyTo");
	       

	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	     
	}
	//Truncating the data from wordpress tables
	static void deleteWPData(Connection dbConnect, String wpPrefix) throws SQLException
	{
		truncateTable(dbConnect,wpPrefix+"comments");
		truncateTable(dbConnect,wpPrefix+"links");
		truncateTable(dbConnect,wpPrefix+"posts");
		truncateTable(dbConnect,wpPrefix+"postmeta");
		truncateTable(dbConnect,wpPrefix+"term_relationships");
		truncateTable(dbConnect,wpPrefix+"term_taxonomy");
		truncateTable(dbConnect,wpPrefix+"terms");
		truncateTable(dbConnect, wpPrefix+"ngg_gallery");
		truncateTable(dbConnect, wpPrefix+"ngg_pictures");
		truncateTable(dbConnect, wpPrefix+"ngg_album");
		
		//We also delete all users except for the first one (the site administrator)
		removeWPUsers(dbConnect,wpPrefix);
	}

	static void createCategories(Connection dbConnect, String wpPrefix, String drPrefix) throws SQLException
	{
		//Retrieving term_data from Drupal
		//At least in my case, vid=3 indicates categories while vid=4 indicates forum topics
		//before launching the script make sure that term_data does not contain redundancies (which anyway indicate an anomalous situation)
		Statement stmt = dbConnect.createStatement();
	    String sqlQuery = "SELECT tid,vid,name FROM " + drPrefix+"term_data WHERE vid=3" ;
		ResultSet resultSet = stmt.executeQuery(sqlQuery);
		String name,slug, sqlUpdate; int tid;
		Statement stmtUpdate = dbConnect.createStatement();
		while (resultSet.next())
		{
		  // For each Drupal category we create the corresponding category in wordpress
		  name=resultSet.getString("name");
		  tid=resultSet.getInt("tid");
		  slug=name.toLowerCase().replace(' ', '_'); //slug are lowercase and without blanks
	      sqlUpdate="INSERT INTO "+ wpPrefix + "terms (term_id, name, slug, term_group) VALUES " +
	      		"("+tid+",'"+name+"','"+slug+"',"+"0"+")";
	      stmtUpdate.executeUpdate(sqlUpdate);
	      System.out.println("Category " + name +" created" );
		}
		stmt.close();
	    stmtUpdate.close();

		//Now we add the taxonomy relations

		Statement stmtTax = dbConnect.createStatement();
	    String sqlQueryTax = "SELECT td.tid, td.description, th.parent FROM " +drPrefix+"term_data td, " + drPrefix+"term_hierarchy th  WHERE td.tid=th.tid and td.vid=3" ;
		ResultSet resultSetTax = stmtTax.executeQuery(sqlQueryTax);
		String descriptionTax,sqlUpdateTax; int tidTax,parentTax;
		Statement stmtUpdateTax = dbConnect.createStatement();
		while (resultSetTax.next())
		{
		  descriptionTax=resultSetTax.getString("description");
		  tidTax=resultSetTax.getInt("tid");
		  parentTax=resultSetTax.getInt("parent");
		  //We use as id of the taxonomy the same id as the term. This assumption is used afterwards
		  //when assigning posts to categories!!
		  sqlUpdateTax="INSERT INTO "+ wpPrefix + "term_taxonomy (term_taxonomy_id, term_id,taxonomy,description,parent,count) VALUES " +
	      		"("+tidTax+","+tidTax+",'"+"category"+"','"+descriptionTax+"',"+parentTax+"," + "0"+")";
	      stmtUpdateTax.executeUpdate(sqlUpdateTax);
	      System.out.println("Category hierarchy " + tidTax + "-" + parentTax+ " created" );

		}
		stmtTax.close();
		stmtUpdateTax.close();
	}
	static void createTags() throws SQLException
	{
		//Retrieving term_data from Drupal
		//At least in my case, vid=3 indicates categories while vid=4 indicates forum topics
		//before launching the script make sure that term_data does not contain redundancies (which anyway indicate an anomalous situation)
		Statement stmt = dbConnect.createStatement();
	    String sqlQuery = "SELECT tid,vid,name FROM " + drPrefix+"term_data WHERE vid=15" ;
		ResultSet resultSet = stmt.executeQuery(sqlQuery);
		String name,slug, sqlUpdate; int tid;
		Statement stmtUpdate = dbConnect.createStatement();
		while (resultSet.next())
		{
		  // For each Drupal category we create the corresponding category in wordpress
		  name = resultSet.getString("name");
		  tid = resultSet.getInt("tid");
		  slug = name.toLowerCase().replace(' ', '_'); //slug are lowercase and without blanks
	      sqlUpdate="INSERT INTO "+ wpPrefix + "terms (term_id, name, slug, term_group) VALUES " +
	      		"("+tid+",'"+name+"','"+slug+"',"+"0"+")";
	      try{
	      stmtUpdate.executeUpdate(sqlUpdate);
	      System.out.println("Tag " + name +" created" );
	      }catch(SQLException e){e.printStackTrace();}
		}
		stmt.close();
	    stmtUpdate.close();

		//Now we add the taxonomy relations

		Statement stmtTax = dbConnect.createStatement();
	    String sqlQueryTax = "SELECT td.tid, td.description, th.parent FROM " +drPrefix+"term_data td, " + drPrefix+"term_hierarchy th  WHERE td.tid=th.tid and td.vid=15" ;
		ResultSet resultSetTax = stmtTax.executeQuery(sqlQueryTax);
		String descriptionTax,sqlUpdateTax; int tidTax,parentTax;
		Statement stmtUpdateTax = dbConnect.createStatement();
		while (resultSetTax.next())
		{
		  descriptionTax=resultSetTax.getString("description");
		  tidTax=resultSetTax.getInt("tid");
		  parentTax=resultSetTax.getInt("parent");
		  //We use as id of the taxonomy the same id as the term. This assumption is used afterwards
		  //when assigning posts to categories!!
		  sqlUpdateTax="INSERT INTO "+ wpPrefix + "term_taxonomy (term_taxonomy_id, term_id,taxonomy,description,parent,count) VALUES " +
	      		"("+tidTax+","+tidTax+",'"+"post_tag"+"','"+descriptionTax+"',"+parentTax+"," + "0"+")";
		  try{
	      stmtUpdateTax.executeUpdate(sqlUpdateTax);
		  }catch(SQLException e){e.printStackTrace();}
	      System.out.println("Tag hierarchy " + tidTax + "-" + parentTax+ " created" );
		}		
		stmtTax.close();
		stmtUpdateTax.close();
	}
	static void createImagesTags() throws SQLException
	{		
		Statement stmtTax = dbConnect.createStatement();
	    String sqlQueryTax = "SELECT term_id, description, parent FROM " + wpPrefix +"term_taxonomy  WHERE taxonomy='post_tag'" ;
		ResultSet resultSetTax = stmtTax.executeQuery(sqlQueryTax);		
		String descriptionTax; int tid, parentTax;
		Statement stmtUpdateTax = dbConnect.createStatement();
		String sqlInsert = "INSERT INTO " + wpPrefix +"term_taxonomy (term_id, taxonomy, description, parent) value (?, ?, ?, ?)";
		PreparedStatement insertStatement = dbConnect.prepareStatement(sqlInsert);
		while (resultSetTax.next())
		{
		  descriptionTax = resultSetTax.getString("description");
		  tid = resultSetTax.getInt("term_id");
		  parentTax = resultSetTax.getInt("parent");
		  insertStatement.setInt(1, tid);
		  insertStatement.setString(2, "ngg_tag");
		  insertStatement.setString(3, descriptionTax);
		  insertStatement.setInt(4, parentTax);
		  //We use as id of the taxonomy the same id as the term. This assumption is used afterwards
		  //when assigning posts to categories!!		  
		  try{
	      insertStatement.executeUpdate();
		  }catch(SQLException e){e.printStackTrace();}	      
		}		
		stmtTax.close();
		stmtUpdateTax.close();
	}
	static void createUser(Connection dbConnect, String wpPrefix, String drPrefix) throws SQLException{
		Statement stmt = dbConnect.createStatement();
		String sqlQuery = "SELECT DISTINCT "+
				"u.uid, u.mail, u.name, u.mail, "+
				"FROM_UNIXTIME(created) created "+
				"FROM "+ drPrefix+ "users u "+				
				"WHERE (1)";
		ResultSet resultSet = stmt.executeQuery(sqlQuery);
		String sqlUpdate="INSERT INTO "+ wpPrefix + "users (ID, user_login, user_pass, user_nicename, user_email, "+
						"user_registered, user_activation_key, user_status, display_name) " +
		 	   		" VALUES (?,?,?,?,?,?,?,?,?) ";
		PreparedStatement stmtUpdate = dbConnect.prepareStatement(sqlUpdate);
		String user_name, user_login,  user_registerd, pass, user_mail;
		pass = "$P$BXlquCE24NijM7DGg.XwPNG.TdqCPW.";
		int uid;
		while (resultSet.next())
		{	
			uid = resultSet.getInt("uid");
			user_mail = resultSet.getString("mail");
			user_name = resultSet.getString("name");
			user_login = user_name.replace(" ", "_").toLowerCase();
			user_registerd = resultSet.getString("created");			
			stmtUpdate.setInt(1, uid);
			stmtUpdate.setString(2, user_login);
			stmtUpdate.setString(3, pass);
			stmtUpdate.setString(4, user_name);
			stmtUpdate.setString(5, user_mail);
			stmtUpdate.setString(6, user_registerd);
			stmtUpdate.setString(7, "");
			stmtUpdate.setInt(8, 0);
			stmtUpdate.setString(9, user_name);
			try{
			stmtUpdate.executeUpdate();
			}catch(Exception e){e.printStackTrace();};
			
		}
		
	}
	static void createFile(Connection dbConnect, String wpPrefix, String drPrefix) throws SQLException
    {
    	Statement stmt = dbConnect.createStatement();
	    String sqlQuery = "SELECT f.uid, f.filepath, f.filemime, FROM_UNIXTIME(f.timestamp) postdate FROM "+ drPrefix+"files f  WHERE fid NOT IN (SELECT fid FROM "+ drPrefix + "image)";
		ResultSet resultSet = stmt.executeQuery(sqlQuery);
		System.out.println(resultSet.getRow());
		String sqlUpdate="INSERT INTO "+ wpPrefix + "posts (post_author, post_date, post_date_gmt, post_title," +
	 	   		"post_status, comment_status, ping_status, post_name, post_type, post_mime_type, post_content, post_excerpt, to_ping, pinged,post_content_filtered)" +
	 	   		" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
		PreparedStatement stmtUpdate = dbConnect.prepareStatement(sqlUpdate);
		String sqlInsertPostmeta = "INSERT INTO " + wpPrefix+"postmeta (post_id, meta_key, meta_value) VALUES (?,?,?)";
		PreparedStatement stmtPostMeta = dbConnect.prepareStatement(sqlInsertPostmeta);
		Statement stmtMaxPostID = dbConnect.createStatement();	    
		int uid, post_id;
		String post_date,  post_title, post_type_mime;
		while (resultSet.next())
		{			
    	   uid = resultSet.getInt("uid");
    	   post_date = resultSet.getString("postdate");
    	   post_type_mime = resultSet.getString("filemime");
    	   post_title = resultSet.getString("filepath");
    	   post_title = post_title.substring(post_title.lastIndexOf("/")+1, post_title.length());
    	   stmtUpdate.setInt(1, uid);
    	   stmtUpdate.setString(2,post_date);
    	   stmtUpdate.setString(3, post_date);
    	   stmtUpdate.setString(4, post_title);
    	   stmtUpdate.setString(5, "inherit");
    	   stmtUpdate.setString(6, "open");
    	   stmtUpdate.setString(7, "open");
    	   stmtUpdate.setString(8, post_title);
    	   stmtUpdate.setString(9, "attachment");
    	   stmtUpdate.setString(10, post_type_mime);
    	   stmtUpdate.setString(11, "");
    	   stmtUpdate.setString(12, "");
    	   stmtUpdate.setString(13, "");
    	   stmtUpdate.setString(14, "");
    	   stmtUpdate.setString(15, "");
    	   //System.out.println(post_title);
    	   //System.out.println(sqlUpdate);
    	   try{
    		   stmtUpdate.executeUpdate();
    	   }catch(Exception e){    	
    		   e.printStackTrace();
    	   }
    	   String sqlQueryMax = "SELECT max(ID) ID FROM "+ wpPrefix+"posts";
   	       ResultSet sqlMaxResultSet = stmtMaxPostID.executeQuery(sqlQueryMax);
   	       sqlMaxResultSet.first();
   	       post_id = sqlMaxResultSet.getInt(1);
   	       stmtPostMeta.setInt(1, post_id);
   	       stmtPostMeta.setString(2, "_wp_attached_file");
   	       String metakeyString = resultSet.getString("filepath").replaceAll("sites/default/files/", "");
   	       //System.out.println(metakeyString);
   	       stmtPostMeta.setString(3, metakeyString);
   	       stmtPostMeta.executeUpdate();
		}
    }
	static void createGallery(Connection dbConnect, String wpPrefix, String drPrefix) throws SQLException{
		Statement stmtUID = dbConnect.createStatement();
		String sqlUIDString = "SELECT uid FROM " + drPrefix +"node WHERE type='image' GROUP BY uid";
		Statement stmt = dbConnect.createStatement();
		ResultSet resultSetUID = stmtUID.executeQuery(sqlUIDString);
		int uid = 1;
		while(resultSetUID.next()){
			uid = resultSetUID.getInt("uid");
			String sqlQueryString = "SELECT f.filepath, n,nid, n.title, FROM_UNIXTIME(n.created) imagedate FROM "+ drPrefix +"image i, "+drPrefix+"node n, " + drPrefix +
									"files f WHERE i.fid = f.fid AND i.nid = n.nid AND i.image_size in ('_original') AND n.uid ="+uid;
			ResultSet resultSet  = stmt.executeQuery(sqlQueryString);
			String pathString = "wp-content/gallery/image_"+uid;
			String name = "image_"+uid;
			String sqlInsertGallery = "INSERT INTO " + wpPrefix +"ngg_gallery (name, slug, path, title, author) VALUES ('"+ name + "', '" + name + "', '" + pathString + "', '"+ name + "', "+uid+")";
			PreparedStatement statement = dbConnect.prepareStatement(sqlInsertGallery, Statement.RETURN_GENERATED_KEYS);
			statement.executeUpdate();
		    ResultSet generatedKeys = statement.getGeneratedKeys();
		    int gid = 0, nid;
		    if (generatedKeys.next()) gid = generatedKeys.getInt(1);
		    String filename, alttext, imagedateString;
		    String insertPicture = "INSERT INTO " + wpPrefix + "ngg_pictures (image_slug, galleryid, filename, alttext, imagedate) VALUES (?, ?, ?, ?, ?)";
		    PreparedStatement insertPictureStatement = dbConnect.prepareStatement(insertPicture, Statement.RETURN_GENERATED_KEYS);
			while (resultSet.next()) {
				filename = resultSet.getString("filepath");
				filename = filename.substring(filename.lastIndexOf("/")+1, filename.length());
				alttext = resultSet.getString("title");
				imagedateString = resultSet.getString("imagedate");
				nid = resultSet.getInt("nid");
				insertPictureStatement.setString(1, filename);
				insertPictureStatement.setInt(2, gid);
				insertPictureStatement.setString(3, filename);
				insertPictureStatement.setString(4, alttext);
				insertPictureStatement.setString(5, imagedateString);
				try{
					insertPictureStatement.executeUpdate();	
					String getTid = "SELECT term_taxonomy_id FROM " + wpPrefix +"term_taxonomy WHERE term_id = SELECT tid FROM "+ drPrefix + "term_node WHERE nid = "+nid;
				}catch(Exception e){e.printStackTrace();}
				//System.out.println(filename);
				//resultSet.next();
				String filethumb = filename.substring(0,filename.lastIndexOf("."));
				filethumb = filethumb+".thumbnail."+filename.substring(filename.lastIndexOf(".")+1,filename.length());
				cutFile(filename, dir, ngg_dir+"_"+uid);
				cutFile(filethumb, dir, ngg_dir+"_"+ uid +"/thumbs");
			}	
		}
	}
	/*static void moveFiles(){
		File folder = new File(dir);
		File[] listOfFiles = folder.listFiles();
		System.out.print(listOfFiles.length);
		for (int i = 0 ; i < listOfFiles.length ; i++){
			if (listOfFiles[i].isFile()){
				String name = listOfFiles[i].getName();
				if (name.indexOf("thumbnail") > 0) {cutFile(name, dir, ngg_dir+"/thumbs");}
				if (name.indexOf("thumbnail") <= 0 && name.indexOf("preview") <= 0){cutFile(name, dir, ngg_dir);}
			}
		}		
	}*/
	static Boolean checkFiles(String wpPrefix,  String filename) throws SQLException{
		Boolean results = true;
		Statement stmt = dbConnect.createStatement();
		String sqlQueryString = "SELECT id FROM " + wpPrefix + "posts WHERE post_content LIKE '%" + filename + "%'";
		ResultSet resultSet  = stmt.executeQuery(sqlQueryString);
		resultSet.last();
		if (resultSet.getRow() > 0) return false;
		return results;
	}
	static void cutFile(String filename, String from, String to){
		CopyOption[] option = new CopyOption[] {
			StandardCopyOption.REPLACE_EXISTING
		};		
		File pathFrom = new File(from+"/"+filename);
		String newname = filename;
		if (newname.indexOf("thumbnail") > 0)
			{
			newname = filename.replace(".thumbnail", "");			
			newname = "thumbs_"+newname;
			}
		File pathTo = new File(to+"/"+newname);		
		if (!pathTo.getParentFile().exists()) pathTo.mkdirs();
		try {
			Files.copy(pathFrom.toPath(),pathTo.toPath(), option[0]);		
			if (checkFiles(wpPrefix, filename)) pathFrom.deleteOnExit();
		} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	//Filling the wp_posts table
    static void createPosts(Connection dbConnect, String wpPrefix, String drPrefix) throws SQLException
    {
    	//Forum post are ignored in this method. All posts were created by the same admin user (if not you'll need to
    	//migrate drupal users, not done here
    	
		Statement stmt = dbConnect.createStatement();
	    String sqlQuery = "SELECT n.nid, n.uid, FROM_UNIXTIME(n.created) created, FROM_UNIXTIME(n.changed) modified, n.TYPE, " +
	    		"n.status, n.title, r.teaser, r.body, u.dst url " +
	    		"FROM " + drPrefix+ "node n, "+ drPrefix + "node_revisions r, "+ drPrefix + "url_alias u " +
	    		"WHERE n.nid=r.nid AND CONCAT('node/',n.nid)=u.src AND n.TYPE IN ('blog','story','page','forum')";
	    //System.out.print(sqlQuery);
		ResultSet resultSet = stmt.executeQuery(sqlQuery);
		int nid,uid,status;
		String created,modified, title,teaser,body,url,type, strStatus,strType;

 	   //SQL insert statement that will be used for each post. The number of comments will be updated later on.
 	   //We use a prepared statement to avoid problems wiht ' and " in the body of the post
 	   String sqlUpdate="INSERT INTO "+ wpPrefix + "posts (id, post_author, post_date, post_date_gmt, post_content, post_title," +
 	   		"post_excerpt,post_status, comment_status, ping_status, post_name, post_parent,menu_order, post_type,comment_count,to_ping,pinged,post_content_filtered)" +
 	   		" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
 	   PreparedStatement stmtUpdate = dbConnect.prepareStatement(sqlUpdate);
 	   int i = 0;
		while (resultSet.next())
		{			
    	   nid=resultSet.getInt("nid");
    	   uid=resultSet.getInt("uid");
    	   status=resultSet.getInt("status");
    	   created=resultSet.getString("created");
    	   modified=resultSet.getString("modified");
    	   title=resultSet.getString("title");
    	   teaser=resultSet.getString("teaser");
    	   body=resultSet.getString("body");
    	   url=resultSet.getString("url");
    	   type=resultSet.getString("type");
    	   //We check if the post is a draft or not
    	   if (status==1) strStatus="publish";
    	   else strStatus="draft";

    	   //Pages and stories are created as pages
    	   //if (type.equals("page") || type.equals("story")) strType="page";
    	   //else
    	   if (title.equals("Impressum") || title.equals("About") || title.equals("Impressum / About")) strType = "page";
    	   else strType="post"; //forum posts and normal posts are both stored as posts
    	   
    	   //To identify forum posts (since we are not migrating them as a separate concept) we prefix the title
    	   if (type.equals("forum")) title="USER FORUM TOPIC " + title;

    	   //URL modification: We take only the last part of the URL (the one belonging to the post itself).
    	   //The pattern structure should be recreated using redirect regular expressions in the site (or
    	   //the permalink wordpress pattern options if possible)
    	   url=url.substring(url.lastIndexOf("/")+1,url.length());

    	   //We now update the internal links to the images in the site
    	   body=body.replaceAll("/sites/default/files/", "wp-content/uploads/");
    	    	   
    	   stmtUpdate.setInt(1,nid);
    	   stmtUpdate.setInt(2,uid);
    	   stmtUpdate.setString(3,created);
    	   stmtUpdate.setString(4,created);
    	   stmtUpdate.setString(5,body);
    	   stmtUpdate.setString(6,title);
    	   stmtUpdate.setString(7,teaser);
    	   stmtUpdate.setString(8,strStatus);
    	   stmtUpdate.setString(9,"open");
    	   stmtUpdate.setString(10,"open");
    	   stmtUpdate.setString(11,url);
    	   stmtUpdate.setInt(12,0);
    	   stmtUpdate.setInt(13,0);
    	   stmtUpdate.setString(14,strType);
    	   stmtUpdate.setInt(15,0);
    	   stmtUpdate.setString(16,"");
    	   stmtUpdate.setString(17,"");
    	   stmtUpdate.setString(18,"");
    	   try{
    	   stmtUpdate.executeUpdate();
    	   }catch(Exception e){}
    	   //System.out.println("Post " + title +" created" );    	   
    	   //resultSet.next();

		}

		Statement stmtPostsCat= dbConnect.createStatement();
    	//Link posts and categories with a global insert. We don't assign categories to spanish blog posts since
		//we don't want them to appear in category-based searches (the spanish version will be stopped, though
		//we still migrate the posts for historical reasons so that previous links to them still work)
		String sqlInsertCat="INSERT INTO "+wpPrefix+"term_relationships (object_id,term_taxonomy_id)" +
				" SELECT t.nid,t.tid FROM " +drPrefix + "term_node t, " + drPrefix + "node n "+
				"WHERE t.nid=n.nid and n.type='blog'";
		stmtPostsCat.executeUpdate(sqlInsertCat);
		
		//Insert Tags
		//Statement stmtPostTagsStatement = dbConnect.createStatement();
		//String sqlInsertTags="INSERT INTO "+wpPrefix+"term_relationships (object_id,term_taxonomy_id)" +
		//		" SELECT t.nid,t.tid FROM " +drPrefix + "term_node t, " + drPrefix + "node n "+
		//		"WHERE t.nid=n.nid and n.type='blog'";

		//Now we can update the count attribute for each category
		String sqlUpdateCount="UPDATE " + wpPrefix+"term_taxonomy tt SET count= " +
				"(SELECT COUNT(tr.object_id) FROM "+ wpPrefix +"term_relationships tr " +
						"WHERE tr.term_taxonomy_id=tt.term_taxonomy_id)";
		stmtPostsCat.executeUpdate(sqlUpdateCount);
		stmt.close();
	    stmtUpdate.close();
	    stmtPostsCat.close();
    }
    /*static void createTags()throws SQLException{
    	Statement stmt = dbConnect.createStatement();
    	String sql = "SELECT t.name, t.tid, v.vid, t.description FROM "+ drPrefix + "term_data t , " +drPrefix + "vocabulary v WHERE t.vid = v.vid AND v.name = 'Tags'";
    	ResultSet resultSet = stmt.executeQuery(sql);
    	String sqlInsert = "INSERT INTO " + wpPrefix + "terms (term_id, name, slug) VALUES (?, ?, ?)";
    	PreparedStatement insertTags = dbConnect.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
    	String sqlInsertTerms = "INSERT INTO "+ wpPrefix + "term_taxonomy (term_id, taxonomy, description, parent, count) VALUES (?,?,?,?,?)";
    	PreparedStatement insertTerm = dbConnect.prepareStatement(sqlInsertTerms);
    	String name, slug;
    	int tid = 0;
    	while(resultSet.next()){
    		tid = resultSet.getInt("tid");
    		name = resultSet.getString("name");
    		slug = name.toLowerCase().replaceAll(" ", "_");
    		insertTags.setInt(1, tid);
    		insertTags.setString(2, name);
    		insertTags.setString(3, slug);
    		try{
    		insertTags.executeUpdate();
    		}catch(SQLException e){e.printStackTrace();}
    		//ResultSet generatedKeys = insertTags.getGeneratedKeys();
		    //if (generatedKeys.next()) tid = generatedKeys.getInt(1);
    		//String sqlInsertRelation = "INSERT INTO " + wpPrefix + "term_t" 
		    insertTerm.setInt(1, tid);
		    insertTerm.setString(2, "post_tag");
		    insertTerm.setString(3, "");
		    insertTerm.setInt(4, 0);
		    insertTerm.setInt(5, 0);
		    insertTerm.executeUpdate();
    	}
    } */

  //Filling the wp_comments table
    static void createComments(Connection dbConnect, String wpPrefix, String drPrefix) throws SQLException
    {
    	//Forum post are ignored in this method. All posts were created by the same admin user (if not you'll need to
    	//migrate drupal users, not done here

		Statement stmt = dbConnect.createStatement();
		//I ignore the hierarchy in the comments. I also ignore comments beloging to forum posts, these will be imported later
	    String sqlQuery = "SELECT c.cid, c.nid, FROM_UNIXTIME(c.timestamp) created, c.comment, c.name, c.mail, c.homepage, c.status"+
	    		" FROM " + drPrefix+ "comments c , " + drPrefix + "node n WHERE c.nid=n.nid and n.type IN ('blog','story','page','forum')";

		int cid,nid,status;
		String created, comment, thread, name, mail,homepage;

 	   //SQL insert statement that will be used for each post. The number of comments will be updated later on.
 	   //We use a prepared statement to avoid problems wiht ' and " in the body of the post
	   //Since I don't migrate the users I ignore as well the uid
 	   String sqlUpdate="INSERT INTO "+ wpPrefix + "comments (comment_id, comment_post_id, comment_author, comment_author_email," +
 	   		"comment_author_url, comment_date, comment_date_gmt, comment_content,comment_approved)" +
 	   		" VALUES (?,?,?,?,?,?,?,?,?) ";
 	   PreparedStatement stmtUpdate = dbConnect.prepareStatement(sqlUpdate);

 	   ResultSet resultSet = stmt.executeQuery(sqlQuery);
	   while (resultSet.next())
	   {
			cid=resultSet.getInt("cid");
	    	nid=resultSet.getInt("nid");
	    	created=resultSet.getString("created");
	    	comment=resultSet.getString("comment");
	    	name=resultSet.getString("name");
	    	mail=resultSet.getString("mail");
	    	homepage=resultSet.getString("homepage");
	    	status=resultSet.getInt("status");
    	    if(status==0) status=1; //the value for approved comments is the reverse one
    	    else status=0;

    	   stmtUpdate.setInt(1,cid);
     	   stmtUpdate.setInt(2,nid);
     	   stmtUpdate.setString(3,name);
     	   stmtUpdate.setString(4,mail);
     	   stmtUpdate.setString(5,homepage);
     	   stmtUpdate.setString(6,created);
     	   stmtUpdate.setString(7,created);
     	   stmtUpdate.setString(8,comment);
     	   stmtUpdate.setInt(9,status);

     	  stmtUpdate.executeUpdate();
   	      System.out.println("Comment " + cid + "for " + nid+ " created" );
	   }

	   Statement stmtCommentPosts= dbConnect.createStatement();
	   //Now we can update the comment count of the posts
	   String sqlUpdateCount="UPDATE " + wpPrefix+"posts p SET p.comment_count= " +
				"(SELECT COUNT(c.comment_post_id) FROM "+ wpPrefix +"comments c " +
						"WHERE c.comment_post_id=p.id)";
		stmtCommentPosts.executeUpdate(sqlUpdateCount);
    }
    

    //Truncate the given table
	static void truncateTable(Connection dbConnect, String name) throws SQLException
	{
		Statement stmt = dbConnect.createStatement();
	    String sql = "TRUNCATE " + name;
	    stmt.executeUpdate(sql);
	}

	static void removeWPUsers(Connection dbConnect, String wpPrefix) throws SQLException
	{
		Statement stmt = dbConnect.createStatement();
	    String sql = "DELETE FROM " + wpPrefix + "users WHERE id > 1";
	    stmt.executeUpdate(sql);
	    sql = "DELETE FROM " + wpPrefix + "usermeta WHERE user_id > 1";
	    stmt.executeUpdate(sql);
	}




}
