package net.amdroid.noitehoje;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.android.maps.GeoPoint;

public class EventInfo {
	private static final int EVT_TYPE_PARTY = 0;
	private static final int EVT_TYPE_SHOW = 1;

	private String id;
	private Date created_at;
	private Date date;
	private Date time;
	private String description;
	private int evt_type;
	private String source;
	private String title;
	private String permalink;
	private Venue venue;

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the created_at
	 */
	public Date getCreated_at() {
		return created_at;
	}

	/**
	 * @return the date
	 */
	public Date getDate() {
		return date;
	}

	/**
	 * @return the time
	 */
	public Date getTime() {
		return time;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @return the evt_type
	 */
	public int getEventType() {
		return evt_type;
	}

	/**
	 * @return the source
	 */
	public String getSource() {
		return source;
	}

	/**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * @return the permalink
	 */
	public String getPermalink() {
		return permalink;
	}

	/**
	 * @return the venue
	 */
	public Venue getVenue() {
		return venue;
	}

	/**
	 * @return the venue
	 */
	public void setVenue(Venue venue) {
		this.venue = venue;
	}

	public EventInfo(String id, String created_at, String date, String time,
			String description, String evt_type, String source, String title,
			String permalink) {

		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

		this.id = id;

		try {
			this.created_at = df.parse(created_at);
			this.date = df.parse(date);
			this.time = df.parse(time);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		this.description = description;
		this.evt_type = parseEventType(evt_type);
		this.source = source;
		this.title = title;
		this.permalink = permalink;
	}
	
	private int parseEventType(String evt_type) {
		if (evt_type.contentEquals("show"))
			return EVT_TYPE_SHOW;
		else if (evt_type.contentEquals("party"))
			return EVT_TYPE_PARTY;
		
		return EVT_TYPE_PARTY;
	}

	public class Venue {
		private String id;
		private String name;
		private String url;
		private String phone;
		private VenueLocation location;
		
		public Venue(String id, String name, String url, String phone) {
			super();
			this.id = id;
			this.name = name;
			this.url = url;
			this.phone = phone;
		}

		/**
		 * @return the id
		 */
		public String getId() {
			return id;
		}


		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}


		/**
		 * @return the url
		 */
		public String getUrl() {
			return url;
		}


		/**
		 * @return the phone
		 */
		public String getPhone() {
			return phone;
		}


		/**
		 * @return the location
		 */
		public VenueLocation getLocation() {
			return location;
		}
		
		public void setLocation(VenueLocation location) {
			this.location = location;
			this.location.setVenueId(this.id);
		}

		public class VenueLocation {
			private String id;
			private String country;
			private String street;
			private String city;
			private String venue_id;
			private GeoPoint location;
			
			public VenueLocation(String id, String country, String street,
					String city, long lat, long lon) {
				super();
				this.id = id;
				this.country = country;
				this.street = street;
				this.city = city;
				this.location = new GeoPoint((int)(lat * 1E6), (int)(lon * 1E6));
			}

			/**
			 * @return the id
			 */
			public String getId() {
				return id;
			}

			/**
			 * @return the country
			 */
			public String getCountry() {
				return country;
			}

			/**
			 * @return the street
			 */
			public String getStreet() {
				return street;
			}

			/**
			 * @return the city
			 */
			public String getCity() {
				return city;
			}

			/**
			 * @return the venue_id
			 */
			public String getVenue_id() {
				return venue_id;
			}

			public GeoPoint getLocation() {
				return location;
			}

			/**
			 * @param venue_id the venue_id to set
			 */
			public void setVenueId(String venue_id) {
				this.venue_id = venue_id;
			}


		}
	}
}
