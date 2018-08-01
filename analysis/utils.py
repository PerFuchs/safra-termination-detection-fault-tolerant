def difference_in_percent(a, b):
	difference = abs(a - b)
	return round(difference / min(a, b) * 100)
