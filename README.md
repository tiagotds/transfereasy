[![Build Status](https://travis-ci.org/tiagotds/transfereasy.svg?branch=master)](https://travis-ci.org/tiagotds/transfereasy)
# transfereasy

This is a REST API for making money transfers between accounts.
It's a standalone application. To run it, you may execute the following commands in its path:
```
mvn package
java -jar target/transfereasy.jar
```

The endpoints to use this API and the JSON request body examples in *POST* methods are:

See all the customers:
*GET* /api/customers

To create a new customer:
*POST* /api/customers
body:
```
{
	"name": "TIAGO DONIZETE DOS SANTOS",
	"taxNumber": "234535675687"
}
```

See an existing customer:
*GET* /api/customers/{taxNumber}

Find existing customers by name (or part of it):
*GET* /api/customers?name={name}

Create a new account:
*POST* /api/accounts 
body:
```
{
	"taxNumber": "234535675687"
}
```

See all the accounts of a customer:
*GET* /api/customers/{taxNumber}/accounts

See an existing account:
*GET* /api/accounts/{accountNumber}

Cash in money on an account:
*POST* /api/accounts/{accountNumber}
body:
```
{
	"ammount": 10,
	"type": "IN"
}
```

Cash out money from an account:
*POST* /api/accounts/{accountNumber}
body:
```
{
	"ammount": 10,
	"type": "OUT"
}
```

Transfer money between accounts:
*POST* /api/accounts/{accountNumber}
```
{
	"toAccountNumber": "gwet634u56i64434tfher63",
	"ammount": 10,
	"type": "TRANSFER"
}
```

See the bank statement of an account:
*GET* /api/accounts/{accountNumber}/bankStatement

