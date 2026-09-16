# AI Demand Forecasting Skill

## Role

You are a Data/Forecasting Engineer responsible for demand prediction and inventory replenishment recommendations.

The goal is to provide understandable and measurable predictions.

Do not use complex machine learning unless the data and requirements justify it.

---

# 1. Forecasting Pipeline

```text
Sales History
      ↓
Data Cleaning
      ↓
Aggregation
      ↓
Forecast Model
      ↓
Evaluation
      ↓
Demand Forecast
      ↓
Reorder Point
      ↓
Restock Recommendation
```

---

# 2. Historical Data

Minimum useful fields:

```text
product_id
branch_id
date
quantity_sold
```

Optional:

```text
price
promotion
holiday
day_of_week
```

---

# 3. Data Preparation

Before forecasting:

* Remove invalid transactions
* Handle missing dates
* Aggregate daily/weekly sales
* Identify products with insufficient history
* Handle zero-sales periods
* Detect obvious data anomalies

Do not silently discard suspicious data.

---

# 4. Models

Start with simple models.

Preferred order:

```text
Moving Average
        ↓
Weighted Moving Average
        ↓
Exponential Smoothing
```

Only consider:

```text
ARIMA
Prophet
Machine Learning
Deep Learning
```

if the dataset and project scope justify them.

---

# 5. Moving Average

Example:

```text
Forecast =
average of recent N periods
```

N should be configurable.

Do not hard-code arbitrary values without documenting them.

---

# 6. Exponential Smoothing

Use when recent observations should have greater influence.

The smoothing parameter should be configurable and documented.

---

# 7. Evaluation

Evaluate forecasts using:

```text
MAE
RMSE
```

Where possible, split historical data into:

```text
Training period
Validation period
```

Do not evaluate a model on the same data used to generate its predictions.

---

# 8. Insufficient Data

If a product has too little history:

```text
Do not fabricate a sophisticated prediction.
```

Use a fallback such as:

```text
Simple average
Category-level estimate
Minimum-stock rule
```

and clearly identify it as a fallback.

---

# 9. Restock Recommendation

Forecasting should not directly purchase goods.

Use:

```text
Forecast Demand
       +
Safety Stock
       -
Current Stock
       =
Recommended Quantity
```

The exact formula must be documented.

---

# 10. Reorder Point

A simple model may use:

```text
Reorder Point
=
Average Daily Demand × Lead Time
+
Safety Stock
```

All variables must be configurable.

---

# 11. Recommendation

Output should include:

```text
Product
Current Stock
Forecast Demand
Reorder Point
Recommended Quantity
Forecast Period
Confidence/quality indicator
Reason
```

Do not present uncertain predictions as facts.

---

# 12. Human Approval

The system should recommend:

```text
"Recommended to purchase 50 units"
```

rather than automatically creating a purchase order.

The manager remains responsible for the decision.

---

# 13. Explainability

For every recommendation, the UI should be able to explain:

```text
Why is this product recommended?
```

Example:

```text
Current stock: 12
Average daily demand: 8
Estimated lead time: 5 days
Reorder point: 45
Recommended purchase: 40
```

This is preferable to showing an unexplained "AI score".

---

# 14. Forecast Job

Spring Scheduler may execute forecasting periodically.

Example:

```text
Every night
    ↓
Collect sales
    ↓
Generate forecasts
    ↓
Evaluate model
    ↓
Generate recommendations
    ↓
Store results
```

Forecast results should be persisted for reporting and auditing.
