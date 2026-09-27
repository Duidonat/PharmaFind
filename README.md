# PharmaFind - Android Application

PharmaFind is an Android application designed to connect customers with local pharmacies for medicine inquiries and availability tracking.

## Overview of Added Functions

### Authentication & Navigation
- **`showLoginDialog()`**: Opens a bottom sheet dialog where users can enter their username and password.
- **`performLogin(username, password, dialog)`**: Authenticates user credentials against the `dbo.Customers` and `dbo.Pharmacies` database tables, saves the session, and redirects users to their respective home activities.
- **`showRegisterSelectionDialog()`**: Displays a modal prompt allowing users to choose whether they want to register as a Customer or a Pharmacy.

### Customer Registration
- **`showRegisterCustomerDialog()`**: Displays the registration form for customers, including an interactive `DatePickerDialog` for selecting birth dates.
- **`registerCustomer(...)`**: Saves the customer profile details (name, address, age, birth date, contact information, and credentials) into the database.

### Pharmacy Registration
- **`showRegisterPharmacyDialog()`**: Displays the sign-up form for pharmacies to register their store details.
- **`registerPharmacy(...)`**: Registers a new pharmacy into the database with an initial `pending` status awaiting administrator approval.

### Session Management
- **`createCustomerSession(...)` & `createPharmacySession(...)`**: Persists user session parameters such as user ID, role, full name, address, and status in `SharedPreferences`.
- **`isLoggedIn()`, `getRole()`, and `logoutUser()`**: Handles user authentication state persistence across application launches and session termination.



HOW TO SETUP DATABASE!!!


SELECT 
    TABLE_NAME, 
    COLUMN_NAME, 
    DATA_TYPE, 
    CHARACTER_MAXIMUM_LENGTH, 
    IS_NULLABLE
FROM 
    INFORMATION_SCHEMA.COLUMNS
WHERE 
    TABLE_NAME IN ('Customers', 'Pharmacies')
ORDER BY 
    TABLE_NAME, ORDINAL_POSITION;



    -- 1. ADD PROFILE & LOCATION FIELDS TO dbo.Customers
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Customers') AND name = 'ProfilePicture')
    ALTER TABLE dbo.Customers ADD ProfilePicture NVARCHAR(255) NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Customers') AND name = 'Bio')
    ALTER TABLE dbo.Customers ADD Bio NVARCHAR(MAX) NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Customers') AND name = 'CreatedAt')
    ALTER TABLE dbo.Customers ADD CreatedAt DATETIME DEFAULT GETDATE();


-- 2. ADD PROFILE, VERIFICATION STATUS, & MAP GEOLOCATION FIELDS TO dbo.Pharmacies
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'Status')
    ALTER TABLE dbo.Pharmacies ADD Status NVARCHAR(20) DEFAULT 'pending'; -- 'pending', 'approved', 'rejected'

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'PermitDocumentUrl')
    ALTER TABLE dbo.Pharmacies ADD PermitDocumentUrl NVARCHAR(255) NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'ProfilePicture')
    ALTER TABLE dbo.Pharmacies ADD ProfilePicture NVARCHAR(255) NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'CoverPhoto')
    ALTER TABLE dbo.Pharmacies ADD CoverPhoto NVARCHAR(255) NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'OperatingHours')
    ALTER TABLE dbo.Pharmacies ADD OperatingHours NVARCHAR(100) DEFAULT '8:00 AM - 8:00 PM';

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'StreetAddress')
    ALTER TABLE dbo.Pharmacies ADD StreetAddress NVARCHAR(255) NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'Latitude')
    ALTER TABLE dbo.Pharmacies ADD Latitude DECIMAL(10, 8) NULL; -- For Google Maps routing

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'Longitude')
    ALTER TABLE dbo.Pharmacies ADD Longitude DECIMAL(11, 8) NULL; -- For Google Maps routing


-- 3. CREATE INQUIRIES TABLE (Community Thread Posts / Medicine Requests)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Inquiries')
BEGIN
    CREATE TABLE dbo.Inquiries (
        InquiryID INT IDENTITY(1,1) PRIMARY KEY,
        CustomerID INT NOT NULL,
        MedicineName NVARCHAR(150) NOT NULL,
        Description NVARCHAR(MAX) NULL,
        ImageUrl NVARCHAR(255) NULL, -- Photo of packaging or prescription
        City NVARCHAR(100) NOT NULL,
        Barangay NVARCHAR(100) NOT NULL,
        Status NVARCHAR(20) DEFAULT 'open', -- 'open', 'fulfilled', 'expired'
        AcceptedPharmacyID INT NULL, -- Populated when customer accepts a reply
        CreatedAt DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (CustomerID) REFERENCES dbo.Customers(CustomerID),
        FOREIGN KEY (AcceptedPharmacyID) REFERENCES dbo.Pharmacies(PharmacyID)
    );
END


-- 4. CREATE PHARMACY_REPLIES TABLE (Restricted to Pharmacy Accounts Only)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'PharmacyReplies')
BEGIN
    CREATE TABLE dbo.PharmacyReplies (
        ReplyID INT IDENTITY(1,1) PRIMARY KEY,
        InquiryID INT NOT NULL,
        PharmacyID INT NOT NULL,
        IsAvailable BIT DEFAULT 1, -- 1 = Available, 0 = Out of stock
        PriceInfo NVARCHAR(100) NULL,
        Note NVARCHAR(MAX) NULL,
        CreatedAt DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (InquiryID) REFERENCES dbo.Inquiries(InquiryID),
        FOREIGN KEY (PharmacyID) REFERENCES dbo.Pharmacies(PharmacyID)
    );
END


IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Customers') AND name = 'District')
    ALTER TABLE dbo.Customers ADD District NVARCHAR(100) NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Pharmacies') AND name = 'District')
    ALTER TABLE dbo.Pharmacies ADD District NVARCHAR(100) NULL;

    SELECT CustomerID, FirstName, Surname, City, Barangay, District, BirthDate, Username FROM dbo.Customers;
