package com.plcoding.stockmarketapp.data.repository


import com.plcoding.stockmarketapp.data.csv.CSVParser
import com.plcoding.stockmarketapp.data.local.StockDatabase
import com.plcoding.stockmarketapp.data.mapper.toCompanylisting
import com.plcoding.stockmarketapp.data.mapper.toCompanylistingEntity
import com.plcoding.stockmarketapp.data.remote.StockApi
import com.plcoding.stockmarketapp.domain.model.CompanyListing
import com.plcoding.stockmarketapp.domain.repository.StockRepository
import com.plcoding.stockmarketapp.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepositoryImpl @Inject constructor(
    private val db: StockDatabase,
    private val api: StockApi,
    private val companyListingsParser: CSVParser<CompanyListing>
): StockRepository {
    override suspend fun getCompanyListings(
        fetchFromRemote: Boolean,
        query: String
    ): Flow<Resource<List<CompanyListing>>> {
        return flow {
            emit(Resource.Loading(true))
            val localListing = db.dao.searchCompanyListing(query)
            emit(Resource.Success(
                data = localListing.map { it.toCompanylisting() }
            ))

            val isDbEmpty = localListing.isEmpty() && query.isBlank()
            val shouldJustLoadFromChche = !isDbEmpty && !fetchFromRemote
            if (shouldJustLoadFromChche) {
                emit(Resource.Loading(false))
                return@flow
            }
            val remoteListing = try {
                val response = api.getListings()
                companyListingsParser.parse(response.byteStream())
            } catch (e: IOException) {
                e.printStackTrace()
                emit(Resource.Error("Couldn't load data"))
                null
            } catch (e: HttpException) {
                e.printStackTrace()
                emit(Resource.Error("Couldn't load data"))
                null
            }

            remoteListing?.let {listings ->
                db.dao.clearCompanyListings()
                db.dao.insertCompanyListings(
                    listings.map {it.toCompanylistingEntity()}
                )
                emit(Resource.Success(
                    data = db.dao
                        .searchCompanyListing("")
                        .map { it.toCompanylisting() }
                ))
                emit(Resource.Loading(false))

            }
        }
    }
}